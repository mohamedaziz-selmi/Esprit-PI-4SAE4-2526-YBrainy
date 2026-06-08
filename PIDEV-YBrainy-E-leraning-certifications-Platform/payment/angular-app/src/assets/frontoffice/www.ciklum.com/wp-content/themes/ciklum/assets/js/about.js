const partnerSwiper = new Swiper('.partnerSwiper', {
    slidesPerView: 2.2,
    speed: 3000,
    loop: true,
    autoplay: {
        delay: 0
    },
    breakpoints: {
        1201: {
            slidesPerView: 5,
        },
        991: {
            slidesPerView: 4,
        },
        481: {
            slidesPerView: 3,
        }
    },
});

// nested tab & Multiple tab
$(document).on("click", ".tabs li", function () {
    var tabContainer = $(this).closest(".comm-tab-wrap");
    var currIndex = $(this).index();
    var currContent = tabContainer
        .children(".tab_container")
        .children(".tab_content");
    tabContainer.find(".tabs li").removeClass("active");
    $(this).addClass("active");
    currContent.removeClass("active");
    currContent.eq(currIndex).addClass("active");
    currContent
        .eq(currIndex)
        .find(".comm-tab-wrap .tabs li")
        .eq(0)
        .trigger("click"); //for nested tab
});

// multiple tab: active indicator of every tab
$(".tabs").each(function () {
    $(this).find("li:first").trigger("click");
});

// for nested tabs: active first tab on click of outer tab
$(document).on("click", ".outer-tab li", function () {
    var currIndex = $(this).index();
    $("#tab" + currIndex)
        .find("li")
        .eq(0)
        .addClass("active");
    $("#tab" + currIndex)
        .find("li")
        .eq(0)
        .trigger("click"); //for indicator
});

function initSwipers() {
    $(".locationSwiper").each(function (i, el) {
        let $el = $(el);

        let swiper = new Swiper(el, {
            slidesPerView: 1,
            spaceBetween: 20,
            navigation: {
                nextEl: $el.find(".location-next")[0],
                prevEl: $el.find(".location-prev")[0],
            },
            speed: 800,
            breakpoints: {
                641: {
                    slidesPerView: 2,
                    spaceBetween: 20,
                },
                1201: {
                    slidesPerView: 3,
                    spaceBetween: 30,
                },
                1441: {
                    slidesPerView: 3,
                    spaceBetween: 60,
                },
            },
        });
    });
}

// Call once on page load
initSwipers();

// Re-init when tab is clicked (since Swiper needs recalculation when hidden -> shown)
$(document).on("click", ".tabs li", function () {
    setTimeout(() => {
        initSwipers();
    }, 300);
});


// Binary Animation Code Starts
const binaryCanvases = [];

function initBinaryCanvas(canvas) {
    const ctx = canvas.getContext('2d');
    const parentElem = canvas.parentElement;

    // Match parent size
    canvas.width = parentElem.clientWidth;
    canvas.height = parentElem.clientHeight;

    const fontSize = 7;
    const columns = Math.floor(canvas.width / fontSize);
    const rows = Math.floor(canvas.height / fontSize);

    ctx.font = `${fontSize}px Arial`;
    ctx.fillStyle = '#fff';

    const binChars = ['0', '1'];
    const bits = [];
    const bitHeight = fontSize;
    const bitWidth = fontSize;

    for (let r = 0; r < rows; r++) {
        for (let c = 0; c < columns; c++) {
            bits.push({
                x: c * bitWidth,
                y: r * bitHeight,
                value: binChars[Math.floor(Math.random() * binChars.length)],
            });
            ctx.fillText(bits.at(-1).value, c * bitWidth, r * bitHeight + bitHeight);
        }
    }

    binaryCanvases.push({ canvas, ctx, bits, fontSize, bitWidth, bitHeight });
}

function animateBinary() {
    const fps = 3;
    const interval = 1000 / fps;
    let then = Date.now();

    function loop() {
        requestAnimationFrame(loop);
        const now = Date.now();
        const delta = now - then;

        if (delta > interval) {
            for (const { canvas, ctx, bits, bitWidth, bitHeight } of binaryCanvases) {
                // âœ… Skip if canvas is offscreen
                const rect = canvas.getBoundingClientRect();
                if (rect.bottom < 0 || rect.top > window.innerHeight) continue;

                // Randomly flip a few bits
                for (let i = 0; i < bits.length * 0.01; i++) {
                    const bit = bits[Math.floor(Math.random() * bits.length)];
                    bit.value = bit.value === '1' ? '0' : '1';
                    ctx.clearRect(bit.x, bit.y, bitWidth, bitHeight);
                    ctx.fillText(bit.value, bit.x, bit.y + bitHeight);
                }
            }
            then = now - (delta % interval);
        }
    }

    loop();
}

document.querySelectorAll('.binary-canvas').forEach(initBinaryCanvas);
animateBinary();
// Binary Animation Code Ends







const cycles = 1;
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
            start: 'top 100%',
            toggleActions: 'play none none reverse',
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


$(document).ready(function () {
    $(".about-video-pop").magnificPopup({
        type: "iframe",
        mainClass: "mfp-fade",
        removalDelay: 160,
        preloader: false,

        fixedContentPos: false,
    });
});


gsap.timeline({
    scrollTrigger: {
        trigger: ".about-banner-wrap",
        start: "0% 100%",
        end: "0% 50%",
        scrub: 1,
        // markers: true,
    }
})
    .to(".big-logo", {
        rotate: 0,
        top: $(".about-video").offset().top - $(".abt-bnr").offset().top,
        width: $(".about-video").width(),
        height: $(".about-video").height(),
        // left: $(".about-video").offset().left
        left: screen.width > 1024 ? $(".big-logo").offset().left - $(".about-video").offset().left : "50%",
        scale: 1.3,
        x: screen.width > 1024 ? "-6%" : "-57%"
    }, 0)
// .to(".about-video-pop", {
// }, 0)
// .to(".big-logo img", {
//     x: $(".about-video").position().left
// }, 0)


console.log($(".about-video").offset().left);


gsap.to(".abt-bnr .comm-head-wrap", {
    opacity: 1,
    duration: 1
})


$(document).on('click', '.tabs li', function () {
    var tabContainer = $(this).closest('.team-wrap')
    var currIndex = $(this).index()
    var currContent = tabContainer.children('.tab_container').children('.tab_content')
    tabContainer.find('.tabs li').removeClass('active');
    $(this).addClass('active')
    currContent.removeClass('active')
    currContent.eq(currIndex).addClass('active')
    currContent.eq(currIndex).find('.team-wrap .tabs li').eq(0).trigger('click'); //for nested tab
})




$(document).on('click', '.exp-tab', function () {
    var tabContainer = $(this).closest('.exp-box');
    var currIndex = $(this).index('.exp-tab');
    var currContent = tabContainer.find('.exp-box-left').children('.exp-box-txt');
    tabContainer.find('.exp-tab').removeClass('active');
    $(this).addClass('active');

    expTabIndex = orderedTabs.indexOf(this);
    currContent.removeClass('active');
    currContent.eq(currIndex).addClass('active');
});
//Experience Tabs Autoplay 
let expTabIndex = $('.exp-tab.active').index('.exp-tab');
let expTimer;
let orderedTabs = [];

function cacheExpTabs() {
    orderedTabs = $('.exp-box .exp-tab')
        .toArray()
        .sort((a, b) => $(a).data('autoplay') - $(b).data('autoplay'));
}
function startExpAuto() {
    stopExpAuto();

    expTimer = setInterval(function () {
        if (!orderedTabs.length) return;

        expTabIndex = (expTabIndex + 1) % orderedTabs.length;
        $(orderedTabs[expTabIndex]).trigger('click');
    }, 3500);
}
function stopExpAuto() {
    clearInterval(expTimer);
}
cacheExpTabs();
startExpAuto();

$('.exp-box').hover(stopExpAuto, startExpAuto);
