/**
 * Created by luke on 2016/09/07.
 */
var grassrootJS = {

    assembleMemberRow : function(listName, newMemberIndex) {
        var strVar="";
        strVar += "<div class=\"form-group member-row inline-field\">";
        strVar += "            <div class=\"col-md-4 col-sm-4 col-xs-12\">";
        strVar += "              <input type=\"text\" class=\"form-control input-lg\" value=\"\" id=\"" + listName + newMemberIndex + ".displayName\" " +
            "name=\"" + listName + "[" + newMemberIndex + "].displayName\" maxlength='25' placeholder='Name' \/>";
        strVar += "            <\/div>";
        strVar += "            <div class=\"col-md-3 col-sm-3 col-xs-12\">";
        strVar += "              <input type=\"text\" class=\"form-control input-lg\" placeholder='Phone' value=\"\" id=\"" + listName + newMemberIndex + ".phoneNumber\" name=\"" + listName + "[" + newMemberIndex + "].phoneNumber\" \/>";
        strVar += "              ";
        strVar += "            <\/div>";
        strVar += "            <div class=\"col-md-4 col-sm-3 col-xs-12\">";
        strVar += "              <select class=\"form-control input-lg\" id=\"" + listName + newMemberIndex + ".roleName\" name=\"" + listName + "[" + newMemberIndex + "].roleName\">";
        strVar += "                <option selected=\"selected\" value=\"ROLE_ORDINARY_MEMBER\">Ordinary member<\/option>";
        strVar += "                <option value=\"ROLE_COMMITTEE_MEMBER\">Committee member<\/option>";
        strVar += "                <option value=\"ROLE_GROUP_ORGANIZER\">Group organizer<\/option>";
        strVar += "              <\/select>";
        strVar += "            <\/div>";
        strVar += "            <div class=\"col-md-1 col-sm-1 col-xs-12\">";
        strVar += "              <i id=\"removeMember" + newMemberIndex + "\" data-index=\"" + newMemberIndex + "\" class=\"fa fa-times row-icon\" aria-hidden=\"true\"></i>";
        strVar += "            <\/div>";
        strVar += "          <\/div>";
        return strVar;
    },

    reduceIndices : function(removedRowIndex, member_table, number_members) {
        console.log("reducing indices, from row = " + removedRowIndex + " with number members = " + number_members);

        var displayName = "\\.displayName";
        var phoneNumber = "\\.phoneNumber";
        var roleName = "\\.roleName";

        for (i = removedRowIndex; i < (number_members - 1); i++) {

            var selectorPrefix = "#listOfMembers" + (i + 1);
            var newPrefix = "listOfMembers[" + i;

            member_table.find(selectorPrefix + displayName).attr('name', newPrefix + "].displayName");
            member_table.find(selectorPrefix + displayName).attr('id', "listOfMembers" + i + ".displayName");

            var phoneSelector = '[id^=\"listOfMembers' + (i+1) + phoneNumber + '\"]';
            var phoneInput = member_table.find(phoneSelector);

            console.log("modifying phone with input : " + phoneInput.val());
            var phoneId = phoneInput.attr('id');
            var phoneSuffix = phoneId.substring(phoneId.indexOf("."));

            console.log("here is the phone suffix: " + phoneSuffix);

            phoneInput.attr('name', "listOfMembers[" + i + "]" + phoneSuffix);
            phoneInput.attr('id', "listOfMembers" + i + phoneSuffix);

            member_table.find(selectorPrefix + roleName).attr('name', "listOfMembers[" + i + "].roleName");
            member_table.find(selectorPrefix + roleName).attr('id', "listOfMembers" + i + ".roleName");

            member_table.find("#removeMember" + (i + 1)).attr("id", "removeMember" + i);

            // console.log("adjusted all indices");
        }

        return (number_members - 1);
    },

    setUpAjax : function(metaCsrfSelector, metaCsrfHeaderSelector) {
        var token = metaCsrfSelector.attr("content");
        var header = metaCsrfHeaderSelector.attr("content");

        $.bind("ajaxSend", function(elm, xhr, s) {
            xhr.setRequestHeader(header, token);
        });
    },

    memberAutoComplete : function(nameInput, phoneInput) {
        // console.log("called member autocomplete");
        return {
            minLength: 2,
            delay: 500,
            source: function(request, response) {
                console.log("triggering source call");
                $.getJSON("/ajax/user/names", { fragment : request.term }, function(data) {
                    console.log("got ajax data back: " + JSON.stringify(data));
                    response(data);
                });
            },
            focus: function(event, ui) {
                event.preventDefault();
                nameInput.val(ui.item.label);
            },
            select: function(event, ui) {
                event.preventDefault();
                phoneInput.val(ui.item.value);
                nameInput.val(ui.item.label);
            }
        };
    },

    UID_VALUE: "UID",
    NAME_VALUE: "NAME",
    PRIVATE_SEARCH: "PRIVATE",
    PUBLIC_SEARCH: "PUBLIC",

    groupNameAutoComplete : function (inputField, returnField, valueType, searchType) {
        return {
            minLength: 1,
            delay: 350,
            source: function(request, response) {
                console.log("finding group names");
                $.getJSON("/ajax/group/names", {
                    fragment : request.term, valueType : valueType, searchType: searchType },
                    function (data) {
                    console.log("got group ajax data back : " + JSON.stringify(data));
                    response(data);
                })
            },
            focus: function(event, ui) {
                event.preventDefault();
                inputField.val(ui.item.label);
            },
            select: function(event, ui) {
                event.preventDefault();
                inputField.val(ui.item.label);
                returnField.val(ui.item.value);
            }
        };
    },
    phoneRules : {
        required: true,
        number: true,
        minlength: 10,
        maxlength: 11,
        messages: {
            required: 'Please enter a phone number',
            minlength: 'Please enter a valid SA cell number',
            maxlength: 'The number you entered is too long'
        }
    },
    groupLimitFields : {
        paidFor : "is_paid_for",
        canAdd: "can_add_to_account",
        hasAcc: "user_has_account"
    }

};