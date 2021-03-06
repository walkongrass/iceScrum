<%@ page import="org.icescrum.core.utils.BundleUtils" %>
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
<script type="text/ng-template" id="actor.multiple.html">

<div class="panel panel-default">
    <div class="panel-heading">
        <h3 class="panel-title">${message(code: "is.ui.actor")} ({{ actors.length }})</h3>
    </div>
    <div class="panel-body">
        <div class="postits standalone">
            <div class="postit-container stack twisted">
                <div style="{{ '#f9f157' | createGradientBackground }}"
                     class="postit actor {{ '#f9f157' | contrastColor }}">
                    <div class="head">
                        <span class="id">{{ topActor.id }}</span>
                    </div>
                    <div class="content">
                        <h3 class="title"
                            ng-model="topActor.name"
                            ng-bind-html="topActor.name | sanitize"
                            ellipsis></h3>
                        <div class="description"
                             ng-model="topActor.description"
                             ng-bind-html="topActor.description | sanitize"
                             ellipsis></div>
                    </div>
                    <div class="tags">
                        <a ng-repeat="tag in topActor.tags" href="#"><span class="tag">{{ tag }}</span></a>
                    </div>
                    <div class="actions">
                        <span class="action">
                            <a tooltip="${message(code: 'todo.is.ui.actions')}" tooltip-append-to-body="true">
                                <i class="fa fa-cog"></i>
                            </a>
                        </span>
                        <span class="action" ng-class="{'active':topActor.attachments.length}">
                            <a tooltip="{{ topActor.attachments.length }} ${message(code:'todo.is.backlogelement.attachments')}"
                               tooltip-append-to-body="true">
                                <i class="fa fa-paperclip"></i>
                            </a>
                        </span>
                        <span class="action" ng-class="{'active':topActor.stories_ids.length}">
                            <a tooltip="{{ topActor.stories_ids.length }} ${message(code:'todo.is.actor.stories')}"
                               tooltip-append-to-body="true">
                                <i class="fa fa-tasks"></i>
                                <span class="badge" ng-show="topActor.stories_ids.length">{{ topActor.stories_ids.length }}</span>
                            </a>
                        </span>
                    </div>
                </div>
            </div>
        </div>
        <div class="btn-toolbar">
            <div ng-if="authorizedActor('delete')"
                 class="btn-group">
                <button type="button"
                        class="btn btn-default"
                        ng-click="confirm({ message: '${message(code: 'is.confirm.delete')}', callback: deleteMultiple })">
                    <g:message code='is.ui.actor.menu.delete'/>
                </button>
            </div>
        </div>
    </div>
</div>
<div class="panel panel-default">
    <table class="table">
        <tr><td>${message(code: 'is.ui.actor.total.stories')}</td><td>{{ sumStories(actors) }}</td></tr>
    </table>
</div>
</script>