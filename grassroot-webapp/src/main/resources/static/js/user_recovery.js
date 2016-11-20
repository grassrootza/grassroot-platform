/**
 * Created by luke on 2016/11/07.
 */

$(document).ready(function () {

    var form = $("#msform");

    console.log("document loaded");

    form.validate({
        rules: {
            mobileNumber: {
                required:true,
                digits: true,
                minlength: 10
            },
            newPassword:{
                required:true,
                minlength: 5
            },
            passwordConfirm: {
                required: true,
                equalTo: "#newPassword",
                minlength: 5
            },
            verificationCode: {
                required: true,
                digits: true
            }
        },
        errorPlacement: function(error, element) {
            if (element.attr("name") == "mobileNumber") {
                error.appendTo($("#numberInvalid"));
            } else if (element.attr("name") == "newPassword" || element.attr("name")) {
                console.log("error on new passwrod");
                error.appendTo($("#passInvalid"));
            } else if (element.attr("name") == "passwordConfirm") {
                error.appendTo($("#passInvalid"));
            } else if (element.attr("name") == "verificationCode") {
                error.appendTo($("#otpInvalid"));
            }
        }
    });

    var current_fs, next_fs, previous_fs; //fieldsets
    var left, opacity, scale; //fieldset properties which we will animate
    var animating; //flag to prevent quick multi-click glitches

    $("#btn-next-phone").click(function() {
        var enteredNumber = $("#mobileNumber");
        if (!enteredNumber.valid()) {
            return false;
        } else {
            var phone = enteredNumber.val();
            var user = phone.trim().replace(/\s/g, '');
            if (user.charAt(0) == '0') {
                user = user.replace("0", "27");
            }

            $("#username").val(user);
            transitionStep(this);
        }
    });

    $("#btn-next-pass").click(function() {
        if ($("#newPassword").valid() && $("#passwordConfirm").valid()) {
            requestOtp(false);
            transitionStep(this);
        } else {
            return false;
        }
    });



    function transitionStep(buttonClicked) {
        if(animating) return false;
        animating = true;

        current_fs = $(buttonClicked).parent();
        next_fs = $(buttonClicked).parent().next();

        $("#progressbar li").eq($("fieldset").index(next_fs)).addClass("active"); //activate next step on progressbar using the index of next_fs

        next_fs.show(); //show the next fieldset
        current_fs.animate({opacity: 0}, { //hide the current fieldset with style
            step: function(now, mx) {
                //1. bring next_fs from the right(50%)
                left = (now * 30)+"%";
                //2. increase opacity of next_fs to 1 as it moves in
                opacity = 1 - now;
                current_fs.css({'transform': 'scale('+scale+')'});
                next_fs.css({'left': left, 'opacity': opacity});
            },
            duration: 300,
            complete: function(){
                current_fs.hide();
                animating = false;
            },
            easing: 'easeOutSine' //this comes from the custom easing plugin
        });
    }

    $(".previous").click(function(){
        if (animating) return false;
        animating = true;

        current_fs = $(this).parent();
        previous_fs = $(this).parent().prev();

        //de-activate current step on progressbar
        $("#progressbar li").eq($("fieldset").index(current_fs)).removeClass("active");

        //show the previous fieldset
        previous_fs.show();
        //hide the current fieldset with style
        current_fs.animate({opacity: 0}, {
            step: function(now, mx) {
                //as the opacity of current_fs reduces to 0 - stored in "now"
                //1. scale previous_fs from 80% to 100%
                //scale = 0.5 + (1 - now) * 0.5;
                //2. take current_fs to the right(50%) - from 0%
                left = ((1-now) * 30)+"%";
                //3. increase opacity of previous_fs to 1 as it moves in
                opacity = 1 - now;
                current_fs.css({'left': left});
                previous_fs.css({'transform': 'scale('+scale+')', 'opacity': opacity});
            },
            duration: 300,
            complete: function(){
                current_fs.hide();
                animating = false;
            },
            //this comes from the custom easing plugin
            easing: 'easeOutSine'
        });
    });

    /*$(".submit").click(function(){
        return false;
    });*/

    $( "#sendVerification" ).click(function(event) {
        event.preventDefault();
        requestOtp(true);
    });

    function requestOtp(showSnackBar) {
        $.ajax({
            url: "/grass-root-verification/" + $("#username").val(),
            context: document.body
        }).done(function() {
            if (showSnackBar) {
                $.snackbar({content: "Verification code sent"});
            }
        });
    }

});