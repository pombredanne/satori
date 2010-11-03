﻿from copy import deepcopy
from satori.client.web.URLDictionary import *
from satori.client.web.queries import *
from satori.client.common.remote import *
from _Widget import Widget

# results table (a possible main content)
class ResultsWidget(Widget):
    pathName = 'results'
    def __init__(self, params, path):
        self.htmlFile = 'htmls/results.html'
        c = ActiveContest(params)
        _params = deepcopy(params)
        d = follow(_params,path)
        if not ('shown' in d.keys()):
            shown = []
        else:
            shown = d['shown']
        if 'user' in d.keys():
            curuser = d['user'][0]
        else:
            curuser = None
        self.isadmin = Privilege.demand(c,"MANAGE")
        self.back_to = ToString(params)
        self.back_path = path
        
        cfdict = {'contest' : c}
        if curuser=='mine' or (not self.isadmin and not curuser):
            cfdict['id']=CurrentContestant(params).id
        elif curuser=='all' or (self.isadmin and not curuser):
            pass
        else:
            cfdict['id'] = int(curuser)
        if self.isadmin:
            self.users = []
            self.users.append(['all','All submits',curuser=='all'])
            self.users.append(['mine','My submits',curuser=='mine'])
            for cont in Contestant.filter({'contest' : c }):
                self.users.append([str(cont.id),cont.name_auto(),curuser==str(cont.id)])
        self.submits = []
        for cont in Contestant.filter(cfdict):
            for o in Submit.filter({'contestant': cont}): # TODO: correct 
                if c.id==o.contestant.contest.id:
                    s = {}
                    id = str(o.id)
                    s["id"] = id
                    s["time"] = o.time
                    s["user"] = o.contestant.name_auto()
                    s["problem"] = o.problem.code
                    s["status"] = o.get_test_suite_status()
                    if not s["status"]:
                        s["status"] = "?"
                    s["details"] = o.get_test_suite_report()
                    _shown = deepcopy(shown)
                    if id in _shown:
                        s["showdetails"] = True
                        _shown.remove(id)
                    else:
                        s["showdetails"] = False
                        _shown.append(id)
                    _shown.sort()
                    d['shown'] = _shown
                    if _shown == []:
                        del d['shown']
                    s["link"] = GetLink(_params,'')
                    self.submits.append(s)
