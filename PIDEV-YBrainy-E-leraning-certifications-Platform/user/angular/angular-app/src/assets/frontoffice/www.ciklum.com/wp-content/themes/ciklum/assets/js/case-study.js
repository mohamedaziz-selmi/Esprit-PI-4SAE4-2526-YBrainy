gsap.registerPlugin(ScrollTrigger);
const cycles = 2;
document.querySelectorAll('.counter-num').forEach((odometer) => {
    const raw = (odometer.dataset.count ?? '0').toString();
    odometer.innerHTML = '';
    const chars = Array.from(raw);
    const digitInners = [];
    chars.forEach((ch, idx) => {
        if (/\d/.test(ch)) {
            const digit = parseInt(ch, 10);
            const d = document.createElement('div');
            d.className = 'digit';
            const inner = document.createElement('div');
            inner.className = 'digit-inner';
            for (let c = 0; c < cycles + 1; c++) {
                for (let n = 0; n <= 9; n++) {
                    const span = document.createElement('span');
                    span.textContent = n;
                    inner.appendChild(span);
                }
            }

            d.appendChild(inner);
            odometer.appendChild(d);
            digitInners.push({ inner, digit, index: idx });
        } else {
            const sep = document.createElement('div');
            sep.className = 'separator';
            sep.textContent = ch;
            odometer.appendChild(sep);
        }
    });
    const tl = gsap.timeline({
        scrollTrigger: {
            trigger: odometer,
            start: 'top 80%',
            toggleActions: 'play none none reverse'
        }
    });

    digitInners.forEach(({ inner, digit, index }) => {
        const oneSpan = inner.querySelector('span');
        const spanH = oneSpan.getBoundingClientRect().height || oneSpan.offsetHeight || 1;
        const offsetY = (cycles * 10 + digit) * spanH;
        tl.to(inner, {
            y: -offsetY,
            duration: 1.5 + (index * 0.1),
            ease: 'power2.out'
        }, index * 0.08);
    });
});

// multiple accordion $('.acc-item').find('.panel').slideUp();
// $(".acc-item").eq(0).find(".panel").slideDown();
// $(".acc-container").each((i, e) => $(e).find(".panel:first").slideDown());
// $(document).on("click", ".acc-item", function () {
//     var accContainer = $(this).closest(".acc-container");
//     accContainer
//         .find(".acc-item")
//         .not(this)
//         .removeClass("active")
//         .find(".panel")
//         .slideUp();
//     $(this).toggleClass("active").find(".panel").slideToggle();
// }); // prevent multiple click on accordion
// $(document).on("click", ".acc-item.active", function () {
//     $(this).css("pointer-events", "none");
//     setTimeout(function () {
//         $(".acc-item").css("pointer-events", "all");
//     }, 1000);
// });


const partnerSwiper = new Swiper('.partnerSwiper', {
    slidesPerView: 2.2,
    speed: 3000,
    loop: true,
    autoplay: {
        delay: 0
    },
    breakpoints: {
        481: {
            slidesPerView: 3,
        }
    },
});
const successSwiper = new Swiper('.successSwiper', {
    slidesPerView: 1,
    speed: 800,
    spaceBetween: 20,
    navigation: {
        nextEl: '.successSwiperNext',
        prevEl: '.successSwiperPrev',
    },
    watchSlidesProgress: true,
    breakpoints: {
        1201: {
            slidesPerView: 3,
            spaceBetween: 60,
        },
        641: {
            slidesPerView: 2,
            spaceBetween: 30,
        }
    }
});

$(".case-study-tabs a").click(function () {
    $(".case-study-tabs a").removeClass("active")
    $(this).addClass("active")
})

var allhead = document.querySelectorAll("[data-custom-heading]")

allhead.forEach((element, i) => {
    gsap.timeline({
        scrollTrigger: {
            trigger: element,
            start: "0% 100%",
            end: "0% 100%",
            scrub: 1,
            // markers: true,
            onEnter: () => headEnter(i),
            onEnterBack: () => headEnter(i - 1)
        }
    })
});


function headEnter(i) {
    if (i >= 0) {
        $(".case-study-tabs a").removeClass("active")
        $(".case-study-tabs a").eq(i).addClass("active")
    }
}