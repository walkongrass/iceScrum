/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */

package org.icescrum.web.presentation.app.project

import grails.converters.XML
import groovy.xml.MarkupBuilder
import org.icescrum.components.UtilsWebComponents
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.support.ProgressSupport

import org.icescrum.core.utils.BundleUtils

import org.springframework.web.servlet.support.RequestContextUtils as RCU

import grails.converters.JSON
import org.icescrum.core.domain.Activity
import grails.plugin.springsecurity.annotation.Secured
import grails.plugin.springsecurity.SpringSecurityUtils
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.PlanningPokerGame
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState
import org.icescrum.core.domain.Sprint
import feedsplugin.FeedBuilder
import com.sun.syndication.io.SyndFeedOutput
import org.apache.commons.io.FilenameUtils
import java.text.DecimalFormat

import static grails.async.Promises.*

@Secured('stakeHolder() or inProduct()')
class ProjectController {

    def productService
    def sprintService
    def teamService
    def releaseService
    def springSecurityService
    def featureService
    def attachmentableService

    @Secured(['isAuthenticated()'])
    def add() {
        render(status:200, template: "dialogs/new")
    }

    @Secured(['isAuthenticated()'])
    def save() {
        def teamParams = params.product?.remove('team')
        def productPreferencesParams = params.product?.remove('preferences')
        def productParams = params.remove('product')

        if (!productParams || !teamParams) {
            returnError(text:message(code:'todo.is.ui.no.data'))
        }

        productParams.startDate = new Date().parse('dd-MM-yyyy', productParams.startDate)
        productParams.endDate = new Date().parse('dd-MM-yyyy', productParams.endDate)

        if (productPreferencesParams.hidden && !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable) && !SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            productPreferencesParams.hidden = true
        }

        //Case user choose to generate sprints
        if (productParams.generateSprints?.after(productParams.endDate) || productParams.generateSprints?.after(productParams.endDate) || productParams.generateSprints == productParams.endDate) {
            def msg = message(code: 'is.product.error.firstSprint')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
            return
        }

        def team
        def product = new Product()
        product.preferences = new ProductPreferences()
        Product.withTransaction { status ->
            bindData(product, productParams, [include:['name','description','startDate','endDate','pkey']])
            bindData(product.preferences, productPreferencesParams, [include:['timezone','noEstimation','displayRecurrentTasks','displayUrgentTasks','estimatedSprintsDuration']])
            try {
                if (!teamParams?.id){
                    team = new Team()
                    bindData(team, teamParams, [include:['name']])
                    def members  = teamParams.members?.id*.collect { it.toLong() }?.flatten() ?: []
                    def scrumMasters = teamParams.scrumMasters?.id*.collect { it.toLong() }?.flatten() ?: []
                    if (!scrumMasters && !members){
                        render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.product.error.noMember')]] as JSON)
                        return
                    }
                    teamService.save(team, members, scrumMasters)
                } else {
                    team = Team.findById(teamParams.id)
                    //TODO
                    // Update old team name
                    //Ugly code
                    //I know
                    //I like it
                    def updateTeamName = team.name.split('team')
                    if (updateTeamName.size() > 1 && (updateTeamName[1].trim().startsWith('201'))){
                        def i = 0
                        team.name = "${updateTeamName[0]}"
                        while(!team.validate()){
                            team.name = "${updateTeamName[0]} ${i}"
                            i++
                        }
                        team.save()
                    }
                }
                def productOwners = productParams.productowners?.id*.collect { it.toLong() }?.flatten() ?: []
                def stakeHolders  = productParams.stakeholders?.id*.collect { it.toLong() }?.flatten() ?: []
                productService.save(product, productOwners, stakeHolders)
                productService.addTeamsToProduct product, [team.id]

                if (productParams.generateSprints){
                    def release = new Release(name: "Release 1", startDate: product.startDate, endDate: product.endDate)
                    releaseService.save(release, product)
                    sprintService.generateSprints(release, productParams.generateSprints)
                }

                render(status:200, contentType: 'application/json', text:product as JSON)
            } catch (IllegalStateException ise) {
                status.setRollbackOnly()
                render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: ise.getMessage())]] as JSON)
            } catch (RuntimeException re) {
                status.setRollbackOnly()
                if (log.debugEnabled) re.printStackTrace()
                render(status: 400, contentType: 'application/json', text: [notice: [text: renderErrors(bean: product) + renderErrors(bean: team)]] as JSON)
            }
        }
    }

    @Secured(['stakeHolder() or inProduct()'])
    def feed(long product) {
        //todo make cache
        Product _product = Product.withProduct(product)
        def activities = Story.recentActivity(_product)
        activities.addAll(Product.recentActivity(_product))
        activities = activities.sort {a, b -> b.dateCreated <=> a.dateCreated}

        def builder = new FeedBuilder()
        builder.feed(description: "${_product.description?:''}",title: "$_product.name ${message(code: 'is.ui.project.activity.title')}", link: "${createLink(absolute: true, controller: 'scrumOS', action: 'index', params: [product: _product.pkey])}") {
          activities.each() { a ->
                entry("${a.poster.firstName} ${a.poster.lastName} ${message(code: "is.fluxiable.${a.code}")} ${message(code: "is." + (a.code == 'taskDelete' ? 'task' : a.code == 'acceptanceTestDelete' ? 'acceptanceTest' : 'story'))} ${a.label.encodeAsHTML()}") {e ->
                    if (a.code != Activity.CODE_DELETE)
                        e.link = "${is.createScrumLink(absolute: true, controller: 'story', id: a.parentRef)}"
                    else
                        e.link = "${is.createScrumLink(absolute: true, controller: 'project')}"
                    e.publishedDate = a.dateCreated
                }
            }
        }
        def feed = builder.makeFeed(FeedBuilder.TYPE_RSS,FeedBuilder.DEFAULT_VERSIONS[FeedBuilder.TYPE_RSS])
        def outFeed = new SyndFeedOutput()
        render(contentType: 'text/xml', text:outFeed.outputString(feed))
    }

    @Secured(['owner()'])
    def delete(long product) {
        try {
            Product _product = Product.withProduct(product)
            productService.delete(_product)
            render(status: 200, contentType: 'application/json', text:[class:'Product',id:product] as JSON)
        } catch (RuntimeException re) {
            if (log.debugEnabled) re.printStackTrace()
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.product.error.not.deleted')]] as JSON)
        }
    }

    @Secured(['permitAll()'])
    def available(String property) {
        def result = false
        //test for name
        if (property == 'name'){
            result = request.JSON.value && Product.countByName(request.JSON.value) == 0
            //test for pkey
        } else if (property == 'pkey'){
            result = request.JSON.value && request.JSON.value =~ /^[A-Z0-9]*$/ && Product.countByPkey(request.JSON.value) == 0
        }
        render(status:200, text:[isValid: result, value:request.JSON.value] as JSON, contentType:'application/json')
    }

    @Secured(['owner() or scrumMaster() or productOwner()'])
    def exportDialog() {
        render(status:200, template: "dialogs/export")
    }

    @Secured(['owner() or scrumMaster() or productOwner()'])
    def export(long product) {
        Product _product = Product.withProduct(product)
        return task {
            session.progress = new ProgressSupport()
            Product.withNewSession {
                withFormat {
                    html {
                        params.zip ? exportProductZIP(_product) : render(text: exportProductXML(_product), contentType: "text/xml")
                    }
                    xml {
                        params.zip ? exportProductZIP(_product) : render(text: exportProductXML(_product), contentType: "text/xml")
                    }
                }
                session.progress.completeProgress(message(code:'todo.is.ui.progress.complete'))
            }
        }
    }

    @Secured('owner() or scrumMaster()')
    def edit(long product) {
        Product _product = Product.withProduct(product)
        def privateOption = !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable)
        if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            privateOption = false
        }
        def menuTagLib = grailsApplication.mainContext.getBean('org.icescrum.core.taglib.MenuTagLib')
        def possibleViews = menuTagLib.getMenuBarFromUiDefinitions(false)

        def dialog = g.render(template: "dialogs/edit",
                model: [product: _product,
                        privateOption: privateOption,
                        possibleViews: possibleViews,
                        restrictedViews:product.preferences.stakeHolderRestrictedViews?.split(',')])
        render(status: 200, contentType: 'application/json', text: [dialog: dialog] as JSON)
    }

    @Secured('(owner() or scrumMaster()) and !archivedProduct()')
    def editPractices(long product) {
        Product _product = Product.withProduct(product)
        def estimationSuitSelect = [(PlanningPokerGame.FIBO_SUITE) : message(code: "is.estimationSuite.fibonacci"),
                                    (PlanningPokerGame.INTEGER_SUITE) : message(code: "is.estimationSuite.integer"),
                                    (PlanningPokerGame.CUSTOM_SUITE) : message(code: "is.estimationSuite.custom")]
        def dialog = g.render(template: "dialogs/editPractices", model: [product: _product, estimationSuitSelect: estimationSuitSelect])
        render(status: 200, contentType: 'application/json', text: [dialog: dialog] as JSON)
    }

    @Secured('(owner() or scrumMaster()) and !archivedProduct()')
    def update() {
        Product product = Product.withProduct(params.long('productd.id'))
        def msg
        if (params.long('productd.version') != product.version) {
            msg = message(code: 'is.stale.object', args: [message(code: 'is.product')])
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
            return
        }
        //Oui pas une faute de frappe c'est bien productd pour pas confondra avec params.product ..... notre id de product
        boolean hasHiddenChanged = product.preferences.hidden != params.productd.preferences.hidden
        product.properties = params.productd
        if(!params.productd.preferences?.stakeHolderRestrictedViews){
            product.preferences.stakeHolderRestrictedViews = null
        }
        try {
            productService.update(product, hasHiddenChanged, product.isDirty('pkey') ? product.getPersistentValue('pkey'): null)
            entry.hook(id:"${controllerName}-${actionName}", model:[product:product])
        } catch (IllegalStateException ise) {
            returnError(exception:ise)
            return
        } catch (RuntimeException re) {
            returnError(exception:re, object:product)
            return
        }
        render(status: 200, contentType: 'application/json', text:product as JSON)
    }

    @Secured('owner() or scrumMaster()')
    def archive(long product) {
        Product _product = Product.withProduct(product)
        try {
            productService.archive(_product)
            render(status: 200, contentType: 'application/json', text:[class:'Product',id:_product.id] as JSON)
        } catch (RuntimeException re) {
            if (log.debugEnabled) re.printStackTrace()
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.product.error.not.archived')]] as JSON)
        }
    }

    @Secured("hasRole('ROLE_ADMIN')")
    def unArchive(long product) {
        Product _product = Product.withProduct(product)
        try {
            productService.unArchive(_product)
            render(status: 200, contentType: 'application/json', text:[class:'Product',id:_product.id] as JSON)
        } catch (RuntimeException re) {
            if (log.debugEnabled) re.printStackTrace()
            render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.product.error.not.archived')]] as JSON)
        }
    }

    @Secured(['stakeHolder() or inProduct()'])
    def versions(long product) {
        Product _product = Product.withProduct(product)
        withFormat{
            html {
                def versions = _product.getVersions(false, true)
                render versions.collect{ [id:it, text:it] } as JSON
            }
            json { renderRESTJSON(text:_product.versions) }
            xml  { renderRESTXML(text:_product.versions) }
        }
    }

    @Secured('inProduct()')
    def addDocument(long product) {
        Product _product = Product.withProduct(product)
        def dialog = g.render(template:'/attachment/dialogs/documents', model:[bean:_product, destController:'project'])
        render status: 200, contentType: 'application/json', text: [dialog: dialog] as JSON
    }

    @Secured('inProduct()')
    def attachments(long product) {
        Product _product = Product.withProduct(product)
        def keptAttachments = params.list('product.attachments')
        def addedAttachments = params.list('attachments')
        def attachments = manageAttachments(_product, keptAttachments, addedAttachments)
        render status: 200, contentType: 'application/json', text: attachments as JSON
    }

    def dashboard(long product) {
        Product _product = Product.withProduct(product)
        def sprint = Sprint.findCurrentOrLastSprint(product).list()[0]
        def release = Release.findCurrentOrNextRelease(product).list()[0]
        def activities = Activity.recentStoryActivity(_product)
        activities.addAll(Activity.recentProductActivity(_product))
        activities = activities.sort {a, b -> b.dateCreated <=> a.dateCreated}

        render template: 'window/view',
                model: [product: product,
                        activities: activities,
                        sprint: sprint,
                        release: release,
                        user: springSecurityService.currentUser,
                        lang: RCU.getLocale(request).toString().substring(0, 2)]
    }

    def productCumulativeFlowChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = productService.cumulativeFlowValues(_product)
        if (values.size() > 0) {
            def rendered = g.render(template: 'charts/productCumulativeFlowChart', model: [
                    suggested: values.suggested as JSON,
                    accepted: values.accepted as JSON,
                    estimated: values.estimated as JSON,
                    planned: values.planned as JSON,
                    inprogress: values.inprogress as JSON,
                    done: values.done as JSON,
                    labels: values.label as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productCumulativeflow.title')],rendered) : rendered, status:200)
        } else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    def productVelocityCapacityChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = productService.productVelocityCapacityValues(_product)
        if (values.size() > 0) {
            def rendered = g.render(template: 'charts/productVelocityCapacityChart', model: [
                    modal: params.modal,
                    capacity: values.capacity as JSON,
                    velocity: values.velocity as JSON,
                    labels: values.label as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productVelocityCapacity.title')],rendered) : rendered, status:200)
        } else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    def productBurnupChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = productService.productBurnupValues(_product)
        if (values.size() > 0) {
            def rendered = g.render(template: 'charts/productBurnupChart', model: [
                    all: values.all as JSON,
                    done: values.done as JSON,
                    labels: values.label as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productBurnUp.title')],rendered) : rendered, status:200)
        } else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    def productBurndownChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = productService.productBurndownValues(_product)
        if (values.size() > 0) {
            def rendered = g.render(template: 'charts/productBurndownChart', model: [
                    userstories: values.userstories as JSON,
                    technicalstories: values.technicalstories as JSON,
                    defectstories: values.defectstories as JSON,
                    labels: values.label as JSON,
                    userstoriesLabels: values*.userstoriesLabel as JSON,
                    technicalstoriesLabels: values*.technicalstoriesLabel as JSON,
                    defectstoriesLabels: values*.defectstoriesLabel as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productBurnDown.title')],rendered) : rendered, status:200)
        } else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    def productVelocityChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = productService.productVelocityValues(_product)
        if (values.size() > 0) {
            def rendered = g.render(template: 'charts/productVelocityChart', model: [
                    userstories: values.userstories as JSON,
                    technicalstories: values.technicalstories as JSON,
                    defectstories: values.defectstories as JSON,
                    labels: values.label as JSON,
                    userstoriesLabels: values*.userstoriesLabel as JSON,
                    technicalstoriesLabels: values*.technicalstoriesLabel as JSON,
                    defectstoriesLabels: values*.defectstoriesLabel as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productVelocity.title')],rendered) : rendered, status:200)
        } else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    def productParkingLotChart(long product) {
        params.modal = params.boolean('modal')
        Product _product = Product.withProduct(product)
        def values = featureService.productParkingLotValues(_product)
        def indexF = 1
        def valueToDisplay = []
        values.value?.each {
            def value = []
            value << new DecimalFormat("#.##").format(it).toString()
            value << indexF
            valueToDisplay << value
            indexF++
        }
        if (valueToDisplay.size() > 0){
            def rendered = g.render(template: '../feature/charts/productParkinglot', model: [
                    values: valueToDisplay as JSON,
                    featuresNames: values.label as JSON,
                    controllerName: params.controllerName ?: controllerName])
            render(text:params.modal ? is.modal([button:[[shortcut:[key:'CTRL+S', title:message(code:'is.button.save.as.image')],text:'<span class="glyphicon glyphicon-save"></span>', class:'save-chart', color:'info']],size:'xxl', title:message(code:'is.chart.productParkinglot.title')],rendered) : rendered, status:200)
        }
        else {
            def msg = message(code: 'is.chart.error.no.values')
            render(status: 400, contentType: 'application/json', text: [notice: [text: msg]] as JSON)
        }
    }

    @Secured('isAuthenticated()')
    def "import"() {
        if (params.flowFilename){
            session.import = [:]
            session.progress = new ProgressSupport()

            def endOfUpload = { uploadInfo ->
                def path
                def xmlFile
                File uploadedProject = new File(uploadInfo.filePath)
                if (FilenameUtils.getExtension(uploadedProject.name) == 'xml'){
                    if (log.debugEnabled){ log.debug 'Export is an xml file, processing now' }
                    xmlFile = uploadedProject
                    path = uploadedProject.absolutePath
                } else if (FilenameUtils.getExtension(uploadedProject.name) == 'zip'){
                    if (log.debugEnabled){ log.debug 'Export is a zipped file, unzipping now' }
                    def tmpDir = ApplicationSupport.createTempDir(FilenameUtils.getBaseName(uploadedProject.name))
                    ApplicationSupport.unzip(uploadedProject,tmpDir)
                    xmlFile = tmpDir.listFiles().find { !it.isDirectory() && FilenameUtils.getExtension(it.name) == 'xml' }
                    path = tmpDir.absolutePath
                } else {
                    render(status:400)
                    return
                }
                def product = productService.parseXML(xmlFile, session.progress)
                def changes = productService.validate(product, session.progress)
                withFormat {
                    html {
                        session.import.product = product
                        session.import.path = path
                        render(status: 200, contentType:'application/json', text:changes as JSON)
                    }
                    xml  {
                        //TODO do saveImport
                        renderRESTXML(text:changes)
                    }
                    json {
                        //TODO do saveImport
                        renderRESTJSON(text:changes)
                    }

                }
            }

            UtilsWebComponents.handleUpload.delegate = this
            UtilsWebComponents.handleUpload(request, params, endOfUpload)

        } else if (session.import) {
            def product = session.import.product
            def path = session.import.path
            if (params.changes){
                def team = product.teams[0]
                if (params.changes?.team?.name) {
                    team.name = params.changes.team.name
                }
                if (params.changes?.users) {
                    team.members?.each {
                        if (params.changes.users."${it.uid}") {
                            it.username = params.changes.users."${it.uid}"
                        }
                    }
                    team.scrumMasters?.each {
                        if (params.changes.users."${it.uid}") {
                            it.username = params.changes.users."${it.uid}"
                        }
                    }
                    product.productOwners?.each {
                        if (params.changes.users."${it.uid}") {
                            it.username = params.changes.users."${it.uid}"
                        }
                    }
                }
            }

            def erase = params.boolean('changes.erase')?:false
            product.pkey = !erase && params.changes?.product?.pkey != null ? params.changes.product.pkey : product.pkey
            product.name = !erase && params.changes?.product?.name != null ? params.changes.product.name : product.name

            def changes = productService.validate(product, session.progress, erase)
            if (!changes){

                Product.withTransaction { status ->
                    try {
                        productService.saveImport(product, path, erase)
                        render(status:200, contentType:'application/json', text:product as JSON)
                    } catch (RuntimeException e) {
                        status.setRollbackOnly()
                        if (log.debugEnabled) e.printStackTrace()
                        render(status: 400, contentType: 'application/json', text: [notice: [text: message(code: 'is.import.error')]] as JSON)
                    } finally {
                        //session.import = null
                    }
                }
            } else {
                session.import.product = product
                session.import.path = path
                render(status:200, contentType:'application/json', text:changes as JSON)
                if (log.infoEnabled) log.info(changes)
            }
        } else {
            render(status:500)
        }
    }

    @Secured(['isAuthenticated()'])
    def importDialog() {
        render(status:200, template: "dialogs/import")
    }

    /**
     * Export the project elements in multiple format (PDF, DOCX, RTF, ODT)
     */
    def print(long product) {
        Product _product = Product.withProduct(product)
        def data
        def chart = null

        if (params.locationHash) {
            chart = processLocationHash(params.locationHash.decodeURL()).action
        }

        switch (chart) {
            case 'productCumulativeFlowChart':
                data = productService.cumulativeFlowValues(_product)
                break
            case 'productBurnupChart':
                data = productService.productBurnupValues(_product)
                break
            case 'productBurndownChart':
                data = productService.productBurndownValues(_product)
                break
            case 'productParkingLotChart':
                data = featureService.productParkingLotValues(_product)
                break
            case 'productVelocityChart':
                data = productService.productVelocityValues(_product)
                break
            case 'productVelocityCapacityChart':
                data = productService.productVelocityCapacityValues(_product)
                break
            default:
                chart = 'timeline'
                data = [
                        [
                                releaseStateBundle: BundleUtils.releaseStates,
                                releases: _product.releases,
                                productCumulativeFlowChart: productService.cumulativeFlowValues(_product),
                                productBurnupChart: productService.productBurnupValues(_product),
                                productBurndownChart: productService.productBurndownValues(_product),
                                productParkingLotChart: featureService.productParkingLotValues(_product),
                                productVelocityChart: productService.productVelocityValues(_product),
                                productVelocityCapacityChart: productService.productVelocityCapacityValues(_product)
                        ]
                ]
                break
        }

        if (data.size() <= 0) {
            returnError(text:message(code: 'is.report.error.no.data'))
        } else if (params.get) {
            renderReport(chart ?: 'timeline', params.format, data, _product.name, ['labels.projectName': _product.name])
        } else if (params.status) {
            render(status: 200, contentType: 'application/json', text: session.progress as JSON)
        } else {
            session.progress = new ProgressSupport()
            def dialog = g.render(template: '/scrumOS/report')
            render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
        }
    }

    def printPostits(long product) {
        Product _product = Product.withProduct(product)
        def stories1 = []
        def stories2 = []
        def first = 0
        def stories = Story.findAllByBacklog(_product, [sort: 'state', order: 'asc'])
        if (!stories) {
            returnError(text:message(code: 'is.report.error.no.data'))
            return
        } else if (params.get) {
            stories.each {
                def testsByState = it.countTestsByState()
                def story = [
                        name: it.name,
                        id: it.uid,
                        effort: it.effort,
                        state: message(code: BundleUtils.storyStates[it.state]),
                        description: is.storyDescription([story: it, displayBR: true]),
                        notes: wikitext.renderHtml([markup: 'Textile'], it.notes).decodeHTML(),
                        type: message(code: BundleUtils.storyTypes[it.type]),
                        suggestedDate: it.suggestedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.suggestedDate]) : null,
                        acceptedDate: it.acceptedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.acceptedDate]) : null,
                        estimatedDate: it.estimatedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.estimatedDate]) : null,
                        plannedDate: it.plannedDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.plannedDate]) : null,
                        inProgressDate: it.inProgressDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.inProgressDate]) : null,
                        doneDate: it.doneDate ? g.formatDate([formatName: 'is.date.format.short', timeZone: _product.preferences.timezone, date: it.doneDate ?: null]) : null,
                        rank: it.rank ?: null,
                        sprint: it.parentSprint?.orderNumber ? g.message(code: 'is.release') + " " + it.parentSprint.parentRelease.orderNumber + " - " + g.message(code: 'is.sprint') + " " + it.parentSprint.orderNumber : null,
                        creator: it.creator.firstName + ' ' + it.creator.lastName,
                        feature: it.feature?.name ?: null,
                        dependsOn: it.dependsOn?.name ? it.dependsOn.uid + " " + it.dependsOn.name : null,
                        permalink:createLink(absolute: true, mapping: "shortURL", params: [product: _product.pkey], id: it.uid),
                        featureColor: it.feature?.color ?: null,
                        nbTestsTocheck: testsByState[AcceptanceTestState.TOCHECK],
                        nbTestsFailed: testsByState[AcceptanceTestState.FAILED],
                        nbTestsSuccess: testsByState[AcceptanceTestState.SUCCESS]
                ]
                if (first == 0) {
                    stories1 << story
                    first = 1
                } else {
                    stories2 << story
                    first = 0
                }

            }
            renderReport('stories', params.format, [[product: _product.name, stories1: stories1 ?: null, stories2: stories2 ?: null]], _product.name)
        } else if (params.status) {
            render(status: 200, contentType: 'application/json', text: session?.progress as JSON)
        } else {
            session.progress = new ProgressSupport()
            def dialog = g.render(template: '/scrumOS/report')
            render(status: 200, contentType: 'application/json', text: [dialog:dialog] as JSON)
        }
    }

    /**
     * Parse the location hash string passed in argument
     * @param locationHash
     * @return A Map
     */
    private processLocationHash(String locationHash) {
        def data = locationHash.split('/')
        return [
                controller: data[0].replace('#', ''),
                action: data.size() > 1 ? data[1] : null
        ]
    }

    private String exportProductXML (Product product) {
        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)
        builder.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
        builder.export(version: meta(name: "app.version")) {
            product.xml(builder)
        }
        def projectName = "${product.name.replaceAll("[^a-zA-Z\\s]", "").replaceAll(" ", "")}-${new Date().format('yyyy-MM-dd')}"
        ['Content-disposition': "attachment;filename=\"${projectName+'.xml'}\"",'Cache-Control': 'private','Pragma': ''].each {k, v ->
            response.setHeader(k, v)
        }
        response.contentType = 'application/octet'
        return writer.toString()
    }

    private void exportProductZIP (Product product) {
        def projectName = "${product.name.replaceAll("[^a-zA-Z\\s]", "").replaceAll(" ", "")}-${new Date().format('yyyy-MM-dd')}"
        def tempdir = System.getProperty("java.io.tmpdir");
        tempdir = (tempdir.endsWith("/") || tempdir.endsWith("\\")) ? tempdir : tempdir + System.getProperty("file.separator")
        def xml = new File(tempdir + projectName + '.xml')
        try {
            xml.withWriter('UTF-8'){ writer ->
                def builder = new MarkupBuilder(writer)
                product.xml(builder)
            }
            def files = []
            product.stories*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            product.actors*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            product.features*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            product.releases*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            product.sprints*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            product.attachments.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            def tasks = []
            product.releases*.each{ it.sprints*.each{ s -> tasks.addAll(s.tasks) } }
            tasks*.attachments.findAll{ it.size() > 0 }?.each{ it?.each{ att -> files << attachmentableService.getFile(att) } }
            ['Content-disposition': "attachment;filename=\"${projectName+'.zip'}\"",'Cache-Control': 'private','Pragma': ''].each {k, v ->
                response.setHeader(k, v)
            }
            response.contentType = 'application/zip'
            ApplicationSupport.zipExportFile(response.outputStream, files, xml, 'attachments')
        } catch (Exception e) {
            if (log.debugEnabled)
                e.printStackTrace()
        } finally {
            xml.delete()
        }
    }

}
