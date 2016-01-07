__author__ = 'aakilomar'
import requests, json, time
requests.packages.urllib3.disable_warnings()

host = "https://localhost:8443"

#from rest_requests import add_user
def add_user(phone):
    post_url = host + "/api/user/add/" + str(phone)
    return requests.post(post_url,None, verify=False).json()

def add_group(userid,phonenumbers):
    post_url = host + "/api/group/add/" + str(userid) + "/" + phonenumbers
    return requests.post(post_url,None, verify=False).json()

#/add/{userId}/{groupId}/{issue}
def add_vote(userid,groupid,issue):
    post_url = host + "/api/vote/add/" + str(userid) + "/" + str(groupid) + "/" + issue
    return requests.post(post_url,None, verify=False).json()

def vote_list():
    list_url = host + "/api/vote/listallfuture"
    r = requests.get(list_url)
    print r.json
    print r.text

def set_event_time(eventid,time):
    post_url = host + "/api/event/settime/" + str(eventid) + "/" + time
    return requests.post(post_url,None, verify=False).json()

def rsvp(eventid,userid,message):
    post_url = host + "/api/event/rsvp/" + str(eventid) + "/" + str(userid) + "/" + str(message)
    return requests.post(post_url,None, verify=False).json()

def add_user_to_group(userid,groupid):
    post_url = host + "/api/group/add/usertogroup/" + str(userid) + "/" + str(groupid)
    return requests.post(post_url,None, verify=False).json()

def manualreminder(eventid,message):
    post_url = host + "/api/event/manualreminder/" + str(eventid) + "/" + str(message)
    return requests.post(post_url,None, verify=False).json()

user = add_user("0826607134")
group = add_group(user['id'],"0821111111")
user2 = add_user("0821111112")
group = add_user_to_group(user2['id'],group['id'])

print user
print group

issue = add_vote(user['id'], group['id'],"test vote")
print issue

#future_votes = vote_list()
#print future_votes

issue = set_event_time(issue['id'],"30th 7pm")
r = rsvp(issue['id'],user['id'],"yes")
r2 = rsvp(issue['id'],user2['id'],"no")
r = rsvp(issue['id'],user['id'],"yes")
ok = manualreminder(issue['id'],"|") # should use reminder mesage
ok = manualreminder(issue['id'],"my manual messsage")






