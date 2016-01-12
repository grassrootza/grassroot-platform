__author__ = 'aakilomar'

import requests, json, time
requests.packages.urllib3.disable_warnings()

host = "https://localhost:8443"

# different project but left as an example
def rewrite_event(event):
    post_url = host + "/api/event/create/"
    headers = {"Authorization" : "(some auth code)", "Accept" : "application/json", "Content-Type" : "application/json"}
    r = requests.post(url=post_url,data=json.dumps(event),headers=headers, verify=False)
    print r.json
    print r.text


def event_list():
    list_url = host + "/api/event/list"
    r = requests.get(list_url, verify=False)
    print r.json
    print r.text

def user_list():
    list_url = host + "/api/user/list"
    return requests.get(list_url, verify=False).json()

#/list/groupandsubgroups/{groupId}
def group_and_subgroup_list(groupid):
    list_url = host + "/api/group/list/groupandsubgroups/" + str(groupid)
    return requests.get(list_url, verify=False).json()

def add_user(phone):
    post_url = host + "/api/user/add/" + str(phone)
    return requests.post(post_url,None, verify=False).json()

def add_group(userid,phonenumbers):
    post_url = host + "/api/group/add/" + str(userid) + "/" + phonenumbers
    return requests.post(post_url,None, verify=False).json()

def add_sub_group(userid,groupid,subgroupname):
    post_url = host + "/api/group/add/subgroup/" + str(userid) + "/" + str(groupid) + "/" + str(subgroupname)
    return requests.post(post_url,None, verify=False).json()

def add_user_to_group(userid,groupid):
    post_url = host + "/api/group/add/usertogroup/" + str(userid) + "/" + str(groupid)
    return requests.post(post_url,None, verify=False).json()

def add_event(userid,groupid, name):
    post_url = host + "/api/event/add/" + str(userid) + "/" + str(groupid) + "/" + name
    return requests.post(post_url,None, verify=False).json()

def add_event_set_subgroups(userid,groupid, name, includesubgroups):
    post_url = host + "/api/event/add/" + str(userid) + "/" + str(groupid) + "/" + name + "/" + includesubgroups
    return requests.post(post_url,None, verify=False).json()

def set_event_location(eventid,location):
    post_url = host + "/api/event/setlocation/" + str(eventid) + "/" + location
    return requests.post(post_url,None, verify=False).json()

#def set_event_day(eventid,day):
#    post_url = host + "/api/event/setday/" + str(eventid) + "/" + day
#    return requests.post(post_url,None, verify=False).json()

def set_event_time(eventid,time):
    post_url = host + "/api/event/settime/" + str(eventid) + "/" + time
    return requests.post(post_url,None, verify=False).json()

def cancel_event(eventid):
    post_url = host + "/api/event/cancel/" + str(eventid)
    return requests.post(post_url,None, verify=False).json()

def rsvp(eventid,userid,message):
    post_url = host + "/api/event/rsvp/" + str(eventid) + "/" + str(userid) + "/" + str(message)
    return requests.post(post_url,None, verify=False).json()

def rsvpTotals(eventid):
    post_url = host + "/api/event/rsvp/totals/" + str(eventid)
    return requests.post(post_url,None, verify=False).json()

def rsvpRequired(userid):
    post_url = host + "/api/event/rsvprequired/" + str(userid)
    return requests.get(post_url,None, verify=False).json()


#add("nogge meeting")
#setlocation(1,"ellispark")
#event = {'name': 'bulls game','location': 'moftus'}
#create(event)

# create user
user = add_user("0826607134")
print user
print "user.id..." + str(user['id'])
#print user_list()

# create group
grouplevel1 = add_group(user['id'],"0821111111 0821111112")
print grouplevel1
print "group.id..." + str(grouplevel1['id'])

# create sub groups
grouplevel2 = add_sub_group(user['id'],grouplevel1['id'],"level 2")
grouplevel3 = add_sub_group(user['id'],grouplevel2['id'],"level 3")

# add users to subgroupd
userlevel2_1 = add_user("0822222221")
userlevel2_2 = add_user("0822222222")
userlevel3_1 = add_user("0823333331")
userlevel3_2 = add_user("0823333332")

grouplevel2 = add_user_to_group(userlevel2_1['id'],grouplevel2['id'])
grouplevel2 = add_user_to_group(userlevel2_2['id'],grouplevel2['id'])

grouplevel3 = add_user_to_group(userlevel3_1['id'],grouplevel3['id'])
grouplevel3 = add_user_to_group(userlevel3_2['id'],grouplevel3['id'])

# create event

event = add_event_set_subgroups(user['id'],grouplevel1['id'],"sub groups and all","true")
print event
event = set_event_location(event['id'],"ellispark")
print event
event = set_event_time(event['id'],"30th 11pm")

# let's rsvp some china's
eventlog = rsvp(event['id'],user['id'],"yes")
print eventlog
eventlog = rsvp(event['id'],userlevel2_1['id'],"yes")
print eventlog
eventlog = rsvp(event['id'],userlevel2_2['id'],"no")
print eventlog
eventlog = rsvp(event['id'],userlevel3_1['id'],"fuckoff")
print eventlog
# wait for mq messagess to be cleared
print "sleeping 3 seconds"
time.sleep(3)

# let's make some changes
event = set_event_location(event['id'],"loftus")
event = set_event_time(event['id'],"30th 10pm")
# wait for mq messagess to be cleared
print "sleeping 3 seconds"
time.sleep(3)

# and now cancel
#event = cancel_event(event['id'])
#print event

totals = rsvpTotals(event['id'])
print totals

userlevel1_3 = add_user("0821111113")
add_user_to_group(userlevel1_3['id'],grouplevel1['id'])

userlevel2_3 = add_user("0822222223")
add_user_to_group(userlevel2_3['id'],grouplevel2['id'])
add_user_to_group(user['id'],grouplevel2['id'])

# lets add lots of events
#
#for idx in range(1,5000,1):
#    event = add_event_set_subgroups(user['id'],grouplevel1['id'],"repeat group " + str(idx),"true")

print "rsvp required for 0826607134...\n"
rsvpreq =  rsvpRequired(user['id'])
for rsvpevent in rsvpreq:
    print rsvpevent

print "rsvp required for 0823333332...\n"
rsvpreq =  rsvpRequired(userlevel3_2['id'])
for rsvpevent in rsvpreq:
    print rsvpevent

# wait for mq messagess to be cleared
print "sleeping 3 seconds"
time.sleep(3)
# get user 0821111111 - add does a load or save
userlevel1_1 = add_user("0821111111")
# rsvp no for 0821111111
eventlog = rsvp(event['id'],userlevel1_1['id'],"no")
# make change and see if 0821111111 gets the change
event = set_event_time(event['id'],"30th 9pm")
#event = set_event_location(event['id'],"new location")

# wait for mq messagess to be cleared
print "sleeping 3 seconds"
time.sleep(3)

#event = set_event_location(event['id'],"zoo lake")

print " get group and subgroups for " + str(grouplevel1['id'])
grouplist = group_and_subgroup_list(grouplevel1['id'])
for group in grouplist:
    print group

print 'klaarie!!!'
