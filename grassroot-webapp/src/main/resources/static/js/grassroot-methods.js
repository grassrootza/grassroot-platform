/**
 * Created by luke on 2016/09/07.
 */
var grassrootJS = {

    assembleMemberRow : function(listName, newMemberIndex) {
        console.log("called assemble member row ...");
        var strVar="";
        strVar += "<tr class=\"member_input_row\">";
        strVar += "            <td>";
        strVar += "              <input type=\"text\" class=\"form-control\" value=\"\" id=\"" + listName + newMemberIndex + ".displayName\" name=\"" + listName + "[" + newMemberIndex + "].displayName\" \/>";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <input type=\"text\" class=\"form-control\" value=\"\" id=\"" + listName + newMemberIndex + ".phoneNumber\" name=\"" + listName + "[" + newMemberIndex + "].phoneNumber\" \/>";
        strVar += "              ";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <select class=\"form-control\" id=\"" + listName + newMemberIndex + ".roleName\" name=\"" + listName + "[" + newMemberIndex + "].roleName\">";
        strVar += "                <option selected=\"selected\" value=\"ROLE_ORDINARY_MEMBER\">Ordinary member<\/option>";
        strVar += "                <option value=\"ROLE_COMMITTEE_MEMBER\">Committee member<\/option>";
        strVar += "                <option value=\"ROLE_GROUP_ORGANIZER\">Group organizer<\/option>";
        strVar += "              <\/select>";
        strVar += "            <\/td>";
        strVar += "            <td>";
        strVar += "              <button type=\"button\" id=\"removeMember\" name=\"removeMember\" class=\"btn btn-default\" aria-label=\"Remove\" value=\"" + newMemberIndex + "\">";
        strVar += "                <span class=\"glyphicon glyphicon-remove\" aria-hidden=\"true\"><\/span>";
        strVar += "              <\/button>";
        strVar += "            <\/td>";
        strVar += "          <\/tr>";
        return strVar;
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