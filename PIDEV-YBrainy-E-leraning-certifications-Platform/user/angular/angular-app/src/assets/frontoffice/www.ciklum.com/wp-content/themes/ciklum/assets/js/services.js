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
                // ✅ Skip if canvas is offscreen
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







const blueCard = new Swiper('.blueCardSwiper', {
    slidesPerView: 1,
    speed: 800,
    spaceBetween: 40,
    breakpoints: {
        1201: {
            slidesPerView: 3,
        },
        901: {
            slidesPerView: 2,
        }
    },
    navigation: {
        nextEl: '.blueCardNext',
        prevEl: '.blueCardPrev',
    },
    watchSlidesProgress: true
});





if (screen.width > 990) {
    const appSwiper = new Swiper(".appSwiper", {
        slidesPerView: 1,
        spaceBetween: 30,
        speed: 800,
        simulateTouch: false,

        autoplay: {
            delay: 5000,
            disableOnInteraction: false,
        },
    });

    let appList = document.querySelectorAll(".app-cont li");

    appList.forEach((el, index) => {
        el.addEventListener("click", () => {
            appList.forEach((item) => item.classList.remove("active"));
            el.classList.add("active");
            appSwiper.slideTo(index);
        });
    });

    appSwiper.on("slideChange", () => {
        let index = appSwiper.activeIndex;
        appList.forEach((item, i) => {
            item.classList.toggle("active", i === index);
        });
    });
} else {
    $(document).ready(function () {
        const $cards = $(".app-card");
        const $infos = $(".app-info p");

        // set first active
        $cards.first().addClass("active");
        $infos.hide().first().fadeIn(); // first paragraph visible

        $cards.on("click", function () {
            const index = $cards.index(this);

            // update active card
            $cards.removeClass("active");
            $(this).addClass("active");

            // fade out others, fade in target
            $infos.hide();
            $infos.eq(index).fadeIn();
        });
    });
}








const industrySwiper = new Swiper('.industrySwiper', {
    slidesPerView: 1,
    speed: 800,
    spaceBetween: 40,
    effect: "fade",
    fadeEffect: {
        crossfade: true
    }
});

$(".industry-acc").click(function () {
    $(".industry-acc").removeClass('active')
    $(this).addClass('active')
    console.log($(this));
    $(this).one('transitionend', function () {
        let indTop = $(this).position().top
        gsap.to(".industry-acc-bg", {
            top: indTop,
            duration: 0.3
        })
    })
    industrySwiper.slideTo($(this).index() - 1)
})

industrySwiper.on('slideChange', function () {
    $(".industry-acc").removeClass('active')
    $(".industry-acc").eq(industrySwiper.activeIndex).addClass("active")
    console.log($(this));
    $(".industry-acc").eq(industrySwiper.activeIndex).one('transitionend', function () {
        let indTop = $(".industry-acc").eq(industrySwiper.activeIndex).position().top
        gsap.to(".industry-acc-bg", {
            top: indTop,
            duration: 0.3
        })
    })
})


const successSwiper = new Swiper('.successSwiper', {
    slidesPerView: 1,
    speed: 800,
    spaceBetween: 20,
    watchSlidesProgress: true,
    navigation: {
        nextEl: '.successSwiperNext',
        prevEl: '.successSwiperPrev',
    },
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


$(document).on('click', '.tabs li', function () {
    var tabContainer = $(this).closest('.industry-tabs')
    var currIndex = $(this).index()
    var currContent = tabContainer.children('.tab_container').children('.tab_content')
    tabContainer.find('.tabs li').removeClass('active');
    $(this).addClass('active')
    currContent.removeClass('active')
    currContent.eq(currIndex).addClass('active')
})

const greyCardSwiper = new Swiper(".greyCardSwiper", {
    slidesPerView: 1,
    spaceBetween: 60,
    speed: 800,
    navigation: {
        nextEl: '.greyCardNext',
        prevEl: '.greyCardPrev',
    },
});


// Industry Section Animation
const parent = document.querySelector('.siBanner');
const child = document.querySelector('.industryHover');
let targetX2 = 0;  // target offset
let targetY2 = 0;
let currentX2 = 0; // current offset
let currentY2 = 0;

function animate2() {
    // simple lerp: current += (target - current) * 0.1
    currentX2 += (targetX2 - currentX2) * 0.1;
    currentY2 += (targetY2 - currentY2) * 0.1;

    child.style.transform = `translate(calc(${currentX2}px), calc(${currentY2}px))`;

    requestAnimationFrame(animate2);
}
animate2();




parent.addEventListener('mousemove', e => {
    const rect = parent.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    targetX2 = (x / rect.width - 0.5) * 420;
    targetY2 = (y / rect.height - 0.5) * 420;
});
