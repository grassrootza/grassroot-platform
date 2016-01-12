__author__ = 'aakilomar'

import requests, json, time
requests.packages.urllib3.disable_warnings()

host = "https://localhost:8443"

def cancel_event(eventid):
    post_url = host + "/api/event/cancel/" + str(eventid)
    return requests.post(post_url,None, verify=False).json()

def add_user(phone):
    post_url = host + "/api/user/add/" + str(phone)
    return requests.post(post_url,None, verify=False).json()

def rsvp(eventid,userid,message):
    post_url = host + "/api/event/rsvp/" + str(eventid) + "/" + str(userid) + "/" + str(message)
    return requests.post(post_url,None, verify=False).json()

def rsvpRequired(userid):
    post_url = host + "/api/event/rsvprequired/" + str(userid)
    return requests.get(post_url,None, verify=False).json()

def voteRequired(userid):
    post_url = host + "/api/event/voterequired/" + str(userid)
    return requests.get(post_url,None, verify=False).json()

def upcomingVotes(groupid):
    post_url = host + "/api/event/upcoming/vote/" + str(groupid)
    return requests.get(post_url,None, verify=False).json()

def upcomingMeeting(groupid):
    post_url = host + "/api/event/upcoming/meeting/" + str(groupid)
    return requests.get(post_url,None, verify=False).json()

def votesPerGroupForEvent(groupid, eventid):
    post_url = host + "/api/event/rsvp/totalspergroup/" + str(groupid) + "/" + str(eventid)
    return requests.post(post_url,None, verify=False).json()

def addLogBook(userid, groupid, message):
    post_url = host + "/api/logbook/add/"  + str(userid) + "/" + str(groupid) + "/" + message
    return requests.post(post_url,None, verify=False).json()

def addLogBookWithDate(userid, groupid, message, actionbydate):
    post_url = host + "/api/logbook/addwithdate/"  + str(userid) + "/" + str(groupid) + "/" + message + "/" + actionbydate
    return requests.post(post_url,None, verify=False).json()

def addLogBookWithDateAndAssign(userid, groupid, message, actionbydate, assigntouserid):
    post_url = host + "/api/logbook/addwithdateandassign/"  + str(userid) + "/" + str(groupid) + "/" + message + "/" + actionbydate + "/" + str(assigntouserid)
    return requests.post(post_url,None, verify=False).json()

def addLogBook(userid, groupid, message, replicate):
    post_url = host + "/api/logbook/add/"  + str(userid) + "/" + str(groupid) + "/" + message + "/" + str(replicate)
    return requests.post(post_url,None, verify=False).json()

def listReplicated(groupid):
    post_url = host + "/api/logbook/listreplicated/"  + str(groupid)
    return requests.get(post_url,None, verify=False).json()

def listReplicated(groupid, completed):
    post_url = host + "/api/logbook/listreplicated/"  + str(groupid) + "/" + str(completed)
    return requests.get(post_url,None, verify=False).json()

def setInitiatedSession(userid):
    post_url = host + "/api/user/setinitiatedsession/"  + str(userid)
    return requests.post(post_url,None, verify=False).json()

def listReplicatedMessage(groupid, message):
    post_url = host + "/api/logbook/listreplicatedbymessage/"  + str(groupid) + "/" + message
    return requests.get(post_url,None, verify=False).json()

def createAccount(userid,groupid,accountname):
    post_url = host + "/api/account/add/"  + str(userid) + "/" + str(groupid) + "/" + str(accountname)
    return requests.post(post_url,None, verify=False).json()

#print cancel_event(5166)
#user = add_user("0823333332")
#user = add_user("0821111111")

#print "user-->" + str(user)
#print rsvp(5167,user['id'],"no")
#print rsvpRequired(user['id'])
#print voteRequired(user['id'])
#print upcomingVotes(231)
#print votesPerGroupForEvent(194,5103)
#print addLogBook(1,85,"X must do Y")
#print addLogBook(1,88,"Somebody must Y",True) # has sub groups
#print addLogBook(1,85,"Somebody must do X",True) # no subgroups
#print listReplicated(88,False)
#print addLogBookWithDateAndAssign(1,21,"aakil must do Y","2015-12-13 08:45:00",588)
#print addLogBookWithDate(1,21,"someone must do Y","2015-12-13 08:45:00")
#print setInitiatedSession(588)
#print(listReplicatedMessage(88,"Somebody must X"))
#print(createAccount(1,21,"acc 21"))