
// multiple accordion $('.acc-item').find('.panel').slideUp();
$(".acc-item").eq(0).find(".panel").slideDown();
$(".acc-container").each((i, e) => $(e).find(".panel:first").slideDown());
$(document).on("click", ".acc-item", function () {
    var accContainer = $(this).closest(".acc-container");
    accContainer
        .find(".acc-item")
        .not(this)
        .removeClass("active")
        .find(".panel")
        .slideUp();
    $(this).toggleClass("active").find(".panel").slideToggle();
}); // prevent multiple click on accordion
$(document).on("click", ".acc-item.active", function () {
    $(this).css("pointer-events", "none");
    setTimeout(function () {
        $(".acc-item").css("pointer-events", "all");
    }, 1000);
});
// $(".case-study-tabs a").click(function () {
//     $(".case-study-tabs a").removeClass("active")
//     $(this).addClass("active")
// })
$(document).on('click', '.tabs li', function () {
    var tabContainer = $(this).closest('.case-study-content-wrap')
    var currIndex = $(this).index()
    var currContent = $(tabContainer).find('.tab_container').children('.tab_content')
    tabContainer.find('.tabs li a').removeClass('active');
    $(this).find('a').addClass('active')
    currContent.removeClass('active')
    currContent.eq(currIndex).addClass('active')
    lenis.scrollTo(".case-study-content", {
        offset: -180,
    })
})


document.addEventListener("DOMContentLoaded", function () {
    // Get hash from URL (e.g. "#section3")
    const hash = window.location.hash;

    if (hash) {
        const element = document.querySelector(hash);
        if (element) {
            element.click();
        }
    }
});