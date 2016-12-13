/**
 * Created by luke on 2016/10/31.
 */

var groupSelect = $("#groupSelect");

var token = $("meta[name='_csrf']");
var header = $("meta[name='_csrf_header']");

var todosRemaining = true;
var remainderLabel = $("#remainingText");

$(document).ready(function() {

    grassrootJS.setUpAjax(token, header);

    var defTime = new Date();
    defTime.setTime(defTime.getTime() + 5*60*1000);

    var reminderSelect = $("#reminderType");
    var assignedMemberCount = $("#pickedMemberCount");

    assignedMemberCount.hide();
    $("#customReminder").hide();

    // if the group selector exists, do the ajax call to populate the member picker & bind refresh to its change function
    if (groupSelect.length > 0) {
        fetchTodosRemaining(groupSelect.find("option:selected").val());
        // fetchParentMemberList(true);

        reminderSelect.changeGroupMinutes();
        groupSelect.change(function() {
            fetchTodosRemaining(groupSelect.find("option:selected").val());
            reminderSelect.changeGroupMinutes();
            $('#wholeGroup').prop("checked", true);
            $('#pickedMemberCount').hide();
            // fetchParentMemberList(false);
        });
    } else {
        fetchTodosRemaining($("#parentUid").val());
    }

    $('#datepicker').datetimepicker({
        format: 'DD/MM/YYYY h:mm A',
        widgetPositioning: {
            horizontal: 'right'
        },
        sideBySide:true,
        minDate: defTime
    });

    $('#pickMembers').click(function(){
        $('#memberModal').modal("show");
    });

    $('#wholeGroup').click(function() {
        assignedMemberCount.hide();
    });

    $('#memberModal').on('hidden.bs.modal', function() {
        var membersSelected = $('#memberModal').find('input[type="checkbox"]:checked').length;
        var countText = "You've assigned " + membersSelected + ((membersSelected == 1) ? " member" : " members") + " to this task";
        assignedMemberCount.text(countText);
        assignedMemberCount.show();
    });

    reminderSelect.change(function() {
        var selected = reminderSelect.find("option:selected").val();
        if (selected == "CUSTOM") {
            $("#customReminder").show();
        } else {
            $("#customReminder").hide();
        }
    });

    $("#createForm").submit(function(event) {
        if (!todosRemaining) {
            $("#limitModal").modal('show');
            event.preventDefault();
        }
    });

});

jQuery.fn.extend({
    changeGroupMinutes: function() {
        var groupSelected = groupSelect.find("option:selected").data("mins");
        var text = "Group default (" + groupSelected + ")"; // todo: i18n at some point
        $(this).find("#groupOption").text(text);
    }
});


function fetchTodosRemaining(groupUid) {
    $.getJSON("/ajax/group/limit/todos", { groupUid: groupUid }, function(data) {
        // add link to account sign up / upgrade
        var hasTodosLeftText = "This group has " + data["todos_left"] + " todos left this month";
        var noTodosLeftAccountLimitReached = "This group has no todos left and your account is full. " +
            "To add more, <a href=\"/account/type\">upgrade</a> your account";
        var noTodosLeftCanAddToAccount = "This group has used up its todos this month. " +
            "<a href=\"javascript:void(0)\" id='addGroupAccount'>Add this group</a> to your account to add more";
        var noTodosLeftAccountFull = "This group has used up its todos and you cannot add more groups to your account. " +
            "<a href=\"/account/type\">Upgrade</a> to add more";
        var noTodosLeftNoAccount = "Sorry, this group has no todos left this month.";
            // + "To add more, <a href=\"/account/signup\">create</a> an account";

        todosRemaining = Number(data["todos_left"]) > 0;

        console.log("return data: " + JSON.stringify(data));
        if (!todosRemaining) {
            remainderLabel.addClass("error-text");
            if (data[grassrootJS.groupLimitFields.paidFor]) {
                remainderLabel.html(noTodosLeftAccountLimitReached);
            } else if (data[grassrootJS.groupLimitFields.canAdd]) {
                remainderLabel.html(noTodosLeftCanAddToAccount);
                $("#addGroupAccount").click(function() {
                    addGroupToAccount(groupUid)
                });
            } else if (data[grassrootJS.groupLimitFields.hasAcc]) {
                remainderLabel.html(noTodosLeftAccountFull);
            } else {
                remainderLabel.html(noTodosLeftNoAccount);
            }
        } else {
            remainderLabel.removeClass("error-text");
            remainderLabel.text(hasTodosLeftText);
        }
    });
}

function addGroupToAccount(groupUid) {
    $.getJSON("/ajax/group/account/add", { groupUid: groupUid }, function(data) {
        // todo : trigger snackbar from here
        console.log("done! group added");
        fetchTodosRemaining(groupUid);
    });
}