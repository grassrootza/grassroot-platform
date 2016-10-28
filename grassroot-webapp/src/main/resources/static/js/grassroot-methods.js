/**
 * Created by luke on 2016/09/07.
 */
var grassrootJS = {

    assembleMemberRow : function(listName, newMemberIndex) {
        console.log("called assemble member row ...");
        var strVar="";
        strVar += "<tr class=\"member_input_row\">";
        strVar += "            <td>";
        strVar += "              <input type=\"text\" class=\"form-control input-lg\" value=\"\" id=\"" + listName + newMemberIndex + ".displayName\" " +
            "name=\"" + listName + "[" + newMemberIndex + "].displayName\" maxlength='25' \/>";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <input type=\"text\" class=\"form-control input-lg\" value=\"\" id=\"" + listName + newMemberIndex + ".phoneNumber\" name=\"" + listName + "[" + newMemberIndex + "].phoneNumber\" \/>";
        strVar += "              ";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <select class=\"form-control input-lg\" id=\"" + listName + newMemberIndex + ".roleName\" name=\"" + listName + "[" + newMemberIndex + "].roleName\">";
        strVar += "                <option selected=\"selected\" value=\"ROLE_ORDINARY_MEMBER\">Ordinary member<\/option>";
        strVar += "                <option value=\"ROLE_COMMITTEE_MEMBER\">Committee member<\/option>";
        strVar += "                <option value=\"ROLE_GROUP_ORGANIZER\">Group organizer<\/option>";
        strVar += "              <\/select>";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <button type=\"button\" id=\"removeMember\" name=\"removeMember\" class=\"btn btn-default btn-lg\" aria-label=\"Remove\" value=\"" + newMemberIndex + "\">";
        strVar += "                <span class=\"glyphicon glyphicon-remove\" aria-hidden=\"true\"><\/span>";
        strVar += "              <\/button>";
        strVar += "            <\/td>";
        strVar += "          <\/tr>";
        return strVar;
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

    // todo : add in field for different kinds of search (part of / owner of / etc)
    groupNameAutoComplete : function(inputField, returnField) {
        return {
            minLength: 1,
            delay: 350,
            source: function(request, response) {
                console.log("finding group names");
                $.getJSON("/ajax/group/names", { fragment : request.term }, function (data) {
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
    }

};