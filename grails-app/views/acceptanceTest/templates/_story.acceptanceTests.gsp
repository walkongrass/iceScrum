<%@ page import="org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState" %>
%{--
- Copyright (c) 2014 Kagilum.
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
- Nicolas Noullet (nnoullet@kagilum.com)
--}%
<script type="text/ng-template" id="story.acceptanceTests.html">
<div ng-if="story">
    <table ng-init="acceptanceTests(story)" class="table" ng-controller="acceptanceTestCtrl">
        <tbody>
        <tr ng-if="authorizedAcceptanceTest('create', editableAcceptanceTest)">
            <td><div ng-include="'story.acceptanceTest.editor.html'"></div></td>
        </tr>
        </tbody>
    </table>
    <table class="table">
        <tr ng-show="story.acceptanceTests === undefined">
            <td class="empty-content">
                <i class="fa fa-refresh fa-spin"></i>
            </td>
        </tr>
        <tr ng-repeat="acceptanceTest in story.acceptanceTests | orderBy:'dateCreated'" ng-controller="acceptanceTestCtrl">
            <td>
                <div class="content">
                    <form name="formHolder.acceptanceTestForm"
                          class="form-editable"
                          ng-class="{ 'form-editing': (formHolder.editing || formHolder.formHover) && authorizedAcceptanceTest('update', editableAcceptanceTest) }"
                          ng-mouseleave="formHover(false)"
                          ng-mouseover="formHover(true)"
                          show-validation
                          novalidate>
                        <div class="clearfix no-padding">
                            <div class="col-sm-1"
                                 ng-switch="(formHolder.editing || formHolder.formHover) && authorizedAcceptanceTest('delete', editableAcceptanceTest)">
                                <button ng-switch-default
                                        class="btn btn-default elemid"
                                        disabled="disabled">{{ editableAcceptanceTest.uid }}</button>
                                <button ng-switch-when="true"
                                        class="btn btn-danger"
                                        ng-click="confirm({ message: '${message(code: 'is.confirm.delete')}', callback: delete, args: [acceptanceTest, story] })"
                                        tooltip-placement="left"
                                        tooltip-append-to-body="true"
                                        tooltip="${message(code:'todo.is.ui.acceptanceTest.delete')}"><span class="fa fa-times"></span>
                                </button>
                            </div>
                            <div class="col-sm-7 form-group">
                                <input required
                                       ng-maxlength="255"
                                       ng-focus="editForm(true)"
                                       ng-blur="update(editableAcceptanceTest, story)"
                                       type="text"
                                       name="name"
                                       ng-model="editableAcceptanceTest.name"
                                       class="form-control"
                                       placeholder="${message(code: 'is.ui.backlogelement.noname')}">
                            </div>
                            <div class="col-sm-4 form-group">
                                <select class="form-control"
                                        ng-focus="editForm(true)"
                                        ng-change="update(editableAcceptanceTest, story)"
                                        name="state"
                                        ng-model="editableAcceptanceTest.state"
                                        ng-readonly="!authorizedAcceptanceTest('updateState', editableAcceptanceTest)"
                                        ui-select2="selectAcceptanceTestStateOptions">
                                    <is:options values="${is.internationalizeValues(map: AcceptanceTestState.asMap())}" />
                                </select>
                            </div>
                        </div>
                        <div class="form-group">
                            <textarea is-markitup
                                      class="form-control"
                                      msd-elastic
                                      ng-maxlength="1000"
                                      name="description"
                                      ng-model="editableAcceptanceTest.description"
                                      is-model-html="editableAcceptanceTest.description_html"
                                      ng-show="showAcceptanceTestDescriptionTextarea"
                                      ng-blur="update(editableAcceptanceTest, story); showAcceptanceTestDescriptionTextarea = false; (editableAcceptanceTest.description.trim() != '${is.generateAcceptanceTestTemplate()}'.trim()) || (editableAcceptanceTest.description = '')"
                                      placeholder="${message(code: 'is.ui.backlogelement.nodescription')}"></textarea>
                            <div class="markitup-preview"
                                 ng-show="!showAcceptanceTestDescriptionTextarea"
                                 ng-click="showAcceptanceTestDescriptionTextarea = true"
                                 ng-focus="editForm(true); showAcceptanceTestDescriptionTextarea = true; editableAcceptanceTest.description || (editableAcceptanceTest.description = '${is.generateAcceptanceTestTemplate()}')"
                                 ng-class="{'placeholder': !editableAcceptanceTest.description_html}"
                                 tabindex="0"
                                 ng-bind-html="(editableAcceptanceTest.description_html ? editableAcceptanceTest.description_html : '<p>${message(code: 'is.ui.backlogelement.nodescription')}</p>') | sanitize"></div>
                        </div>
                    </form>
                </div>
            </td>
        </tr>
        <tr ng-show="!story.acceptanceTests.length">
            <td class="empty-content">
                <small>${message(code:'todo.is.ui.acceptanceTest.empty')}</small>
            </td>
        </tr>
    </table>
</div>
</script>