/**
 * Recreated by luke on 2017/11/18.
 */

var groupSelect = $("#groupSelect");

var token = $("meta[name='_csrf']");
var header = $("meta[name='_csrf_header']");

$(document).ready(function() {

    grassrootJS.setUpAjax(token, header);

    var defTime = new Date();
    defTime.setTime(defTime.getTime() + 5*60*1000);

    var typeSelector = $("#type");
    var responseLabel = $("#resp_label_holder");
    var assignMemberRadio = $("#assign_member_holder");
    var validateMemberRadio = $("#validating_member_holder");

    responseLabel.hide();
    assignMemberRadio.hide();
    validateMemberRadio.hide();

    typeSelector.change(function() {
        var typeSelected = typeSelector.val();
        switch (typeSelected) {
          case 'INFORMATION_REQUIRED':
              responseLabel.show();
              assignMemberRadio.hide();
              validateMemberRadio.hide();
              break;
          case 'ACTION_REQUIRED':
              responseLabel.hide();
            assignMemberRadio.hide();
            validateMemberRadio.hide();
            break;
          case 'VOLUNTEERS_NEEDED':
              responseLabel.hide();
              assignMemberRadio.hide();
              validateMemberRadio.hide();
            break;
          case 'VALIDATION_REQUIRED':
            responseLabel.hide();
            assignMemberRadio.show();
            validateMemberRadio.show();
            break;
        }
    });

    // if the group selector exists, do the ajax call to populate the member picker & bind refresh to its change function
    if (groupSelect.length > 0) {
        // fetchParentMemberList(true);
        groupSelect.change(function() {
            $('#wholeGroup').prop("checked", true);
            $('#pickedMemberCount').hide();
            // fetchParentMemberList(false);
        });
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

});
