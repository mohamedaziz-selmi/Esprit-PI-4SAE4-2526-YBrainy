// page scroll to top on refresh
// =======================================================

// window.addEventListener('beforeunload', function () {
//     window.scrollTo(0, 0);
// });

/*================================= Sticky Header Starts =================================*/

function fixedHeader() {
    var sticky = $('#header'),
        scroll = $(window).scrollTop();
    if (scroll >= 10) sticky.addClass('fixHeader');
    else sticky.removeClass('fixHeader');
}

$(window).scroll(function (e) {
    fixedHeader();
});
fixedHeader();
/* Sticky Header Ends */


// $('#header').load('header.html', function () {
// fixedHeader();
if ($(window).outerWidth() < 990) {
    var MobileMenu = new MobileNav({
        initElem: "nav",
        menuTitle: "Menu",
    });
}
const navItems = document.querySelectorAll(".nav-item");
navItems.forEach((item) => {
    const hasDropdowns = item.querySelector(".dropdown") !== null;
    if (hasDropdowns) {
        item.classList.add("dr-icon");
    }
});

var dropLinks = document.querySelectorAll(".drop-list-links");
var dropList = document.querySelectorAll(".drop-list-tabs li");
dropList.forEach((element, i) => {
    $(element).mouseenter(function () {
        $(".drop-list-tabs li").removeClass("active")
        $(this).addClass("active");
        $(".drop-list-links").removeClass("active")
        $(dropLinks[i]).addClass("active")
    })
});

$(".drop-big").mouseenter(function () {
    var allList = $(this).find(".drop-list-tabs li")
    var allListTab = $(this).find(".drop-list-links")
    $(allList).removeClass("active")
    $(allListTab).removeClass("active")
    $(allList[0]).addClass("active")
    $(allListTab[0]).addClass("active")
})
// });

// $('#footer').load('footer.html');


/* Form Feild Functionality */
$(document).on('input', '.form-field', function () {
    if ($(this).val().length > 0) {
        $(this).addClass('field--not-empty');
    } else {
        $(this).removeClass('field--not-empty');
    }
});
/* Form Feild Functionality ends */

/* Fade Animation Starts */
document.addEventListener("DOMContentLoaded", () => {
    const animationMap = {
        'blur': 'text-focus-in',
        'fade-up': 'fade-in-bottom',
    };

    const applyAnimation = (entry) => {
        const animType = entry.target.getAttribute('data-anim');
        const animClass = animationMap[animType];

        if (animClass) {
            entry.target.classList.add(animClass);
            observer.unobserve(entry.target); // Animate only once
        }
    };

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                applyAnimation(entry);
            }
        });
    }, {
        threshold: 0
    });

    document.querySelectorAll('[data-anim]').forEach(el => observer.observe(el));
});
/* Fade Animation Ends */

/* // Initialize Lenis */
const lenis = new Lenis({
    smooth: true
});
function smoothScrollOnAnchorClick() {
    let headerHeight = window.getComputedStyle(document.body).getPropertyValue('--header-height');


    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
        anchor.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = this.getAttribute('href');
            const targetElement = document.querySelector(targetId);

            if (targetElement) {
                let offset = 0;
                offset = -parseInt(headerHeight)

                lenis.scrollTo(targetElement, {
                    duration: 1, // Adjust duration as needed
                    easing: (t) => 1 - Math.pow(1 - t, 3), // Optional easing function
                    offset: offset
                });
            }
        });
    });
}

smoothScrollOnAnchorClick();

function raf(time) {
    lenis.raf(time);
    requestAnimationFrame(raf);
}
requestAnimationFrame(raf);


gsap.ticker.add((time) => {
    lenis.raf(time * 1000); // Convert time from seconds to milliseconds
});

// Disable lag smoothing in GSAP to prevent any delay in scroll animations
gsap.ticker.lagSmoothing(0);



// multiple accordion $('.acc-item').find('.panel').slideUp();
$(".acc-item").eq(0).find(".panel").slideDown();
$(".acc-container").each((i, e) => $(e).find(".panel:first").slideDown());
$(document).on("click", ".acc-item", function() {
    var accContainer = $(this).closest(".acc-container");
    accContainer
        .find(".acc-item")
        .not(this)
        .removeClass("active")
        .find(".panel")
        .slideUp();
    $(this).toggleClass("active").find(".panel").slideToggle();
}); // prevent multiple click on accordion
$(document).on("click", ".acc-item.active", function() {
    $(this).css("pointer-events", "none");
    setTimeout(function() {
        $(".acc-item").css("pointer-events", "all");
    }, 1000);
});

