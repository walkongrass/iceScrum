%{--
- Copyright (c) 2010 iceScrum Technologies.
-
- This file is part of iceScrum.
-
- iceScrum is free software: you can redistribute it and/or modify
- it under the terms of the GNU Affero General Public License as published by
- the Free Software Foundation, either version 3 of the License.
-
- iceScrum is distributed in the hope that it will be useful,
- but WITHOUT ANY WARRANTY; without even the implied warranty of
- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
- GNU General Public License for more details.
-
- You should have received a copy of the GNU Affero General Public License
- along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
-
- Authors:
-
- Vincent Barrier (vbarrier@kagilum.com)
--}%
<%@ page import="grails.plugin.springsecurity.SpringSecurityUtils" %>
<g:form action="updateVision" method="post" name="visionForm" class="box-form box-form-200-legend">
    <is:fieldset title="is.ui.releasePlan.vision.properties.title">
        <is:fieldArea for="vision" label="is.release.vision" noborder="true" class="rich-large">
            <is:area
                    rich="[ preview:true,
                      fillWidth:true,
                      margin:250,
                      height:250,
                      disabled:sec.noAccess(expression:'productOwner()',{true})]"
                    id="vision"
                    name="vision"
                    value="${release?.vision}"/>
        </is:fieldArea>
    </is:fieldset>

    <is:buttonBar>
        <sec:access expression="productOwner()">
            <is:button
                    targetLocation="${controllerName+'/'+actionName}/${release.id}"
                    id="submitForm" type="submitToRemote"
                    url="[controller:controllerName, action:'updateVision', params:[product:params.product,id:params.id]]"
                    value="${message(code:'is.ui.releasePlan.vision.button.save')}"
                    onSuccess="jQuery.icescrum.renderNotice('${message(code:'is.release.vision.saved')}');"/>
        </sec:access>
        <is:button type="link" button="button-s button-s-black" remote="true"
                   url="[controller:controllerName, action:'index',id:params.id,params:[product:params.product]]"
                   update="window-content-${controllerName}">${message(code: 'is.button.close')}</is:button>
    </is:buttonBar>
</g:form>