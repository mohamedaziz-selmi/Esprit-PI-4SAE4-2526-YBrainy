gsap.registerPlugin(ScrollTrigger, SplitText);

function stopLenis() {
    lenis.stop()
    // document.documentElement.style.overflow = 'hidden'      
    // document.documentElement.style.scrollbarWidth = 'none'  
    // document.documentElement.style.msOverflowStyle = 'none' 
    // document.documentElement.style.setProperty('--webkit-scrollbar-display', 'none')
}
stopLenis()
gsap.to(".tl-circle", {
    opacity: 1,
    scale: 1,
    duration: 1.5,
    ease: "power2.out"
})
gsap.to(".loading-text ", {
    opacity: 1,
    duration: 0,
})


// let split = SplitText.create(".loading-text", { type: "chars" });
// gsap.from(split.chars, {
//     y: "random(50, 150)",
//     opacity: 0,
//     stagger: 0.05,
//     duration: 1.5
// });


function startLenis() {
    lenis.start()
    // document.documentElement.style.overflow = ''
    // document.documentElement.style.scrollbarWidth = ''
    // document.documentElement.style.msOverflowStyle = ''
    // document.documentElement.style.removeProperty('--webkit-scrollbar-display')
}

setTimeout(() => {
    gsap.to(".tl-circle", {
        width: screen.width > 1024 ? "52%" : "130%",
        duration: 1,
        top: screen.width > 640 ? "-16%" : "10vw",
    })
    // gsap.to(split.chars, {
    //     y: "random(50, 150)",
    //     opacity: 0,
    //     stagger: 0.05,
    //     duration: 0.5
    // });
    gsap.to(".loading-text ", {
        opacity: 0,
        duration: 1,
    })
    gsap.to(".cross-anim ", {
        opacity: 1,
        duration: 1,
    })
    gsap.to(".home-header,.main-head,.banner-bottom", {
        opacity: 1,
        duration: 1,
    })
    gsap.from(".main-head,.banner-bottom", {
        y: 100,
        duration: 1,
    })
    gsap.to(".tl-circle video", {
        width: "60%",
        filter: "blur(40px)",
        duration: 1,
    })
    // let splitHead = SplitText.create(".main-head", { type: "chars" });
    // gsap.from(splitHead.chars, {
    //     y: 20,
    //     opacity: 0,
    //     stagger: 0.05,
    //     duration: 0.8
    // });
    gsap.to(".cross-anim-1,.cross-anim-2,.cross-anim-3 ", {
        x: 0,
        y: 0,
        opacity: 1,
        delay: 0.8,
        duration: 1,
        ease: "back.out(1.5)"
    });
    startLenis()
}, 2000);

const trustSwiper = new Swiper('.trustSwiper', {
    slidesPerView: 1.8,
    speed: 3000,
    spaceBetween: 20,
    loop: true,
    // enable observers so Swiper detects layout changes (pinning/transform/fixed)
    observer: true,
    observeParents: true,
    // autoplay - pauseOnMouseEnter false helps inside pinned/fixed containers
    autoplay: {
        delay: 1,
        disableOnInteraction: false,
        pauseOnMouseEnter: false,
    },
    watchSlidesProgress: true,
    watchOverflow: true,
    breakpoints: {
        1201: { slidesPerView: 5 },
        991: { slidesPerView: 4 },
        481: { slidesPerView: 3 }
    },
});

// Helper to safely start/update swiper when layout is stable
function ensureSwiperRunning(swiper) {
    try {
        swiper.update(); // recalculates sizes
        // start autoplay only when page is visible (Safari may block)
        if (document.visibilityState === 'visible') {
            swiper.autoplay.start();
        } else {
            // when tab becomes visible, start autoplay
            const onVis = () => {
                if (document.visibilityState === 'visible') {
                    swiper.autoplay.start();
                    document.removeEventListener('visibilitychange', onVis);
                }
            };
            document.addEventListener('visibilitychange', onVis);
        }
    } catch (e) {
        console.warn('Swiper start/update failed:', e);
    }
}

// Try start once after your loading/entry animations finish (replace or augment your existing setTimeout)
setTimeout(() => {
    ensureSwiperRunning(trustSwiper);
}, 600); // small delay to let GSAP initial layout finish

// Also run after any ScrollTrigger refreshes (important when pinning/transform changes layout)
if (window.ScrollTrigger) {
    ScrollTrigger.addEventListener('refresh', () => ensureSwiperRunning(trustSwiper));
    // call refresh once after setup so ScrollTrigger settles
    ScrollTrigger.refresh();
}

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


// Banner Hover Animation Starts
const mainWrap = document.querySelector('.banner-wrap');
const mainChildVid = document.querySelector('.tl-circle video');

let targetX = 0;
let targetY = 0;
let currentX = 0;
let currentY = 0;

function animate() {
    const dx = targetX - currentX;
    const dy = targetY - currentY;

    if (Math.abs(dx) < 0.1 && Math.abs(dy) < 0.1) {
        currentX = targetX;
        currentY = targetY;
    } else {
        currentX += dx * 0.08;
        currentY += dy * 0.08;
    }

    mainChildVid.style.transform = `translate(${currentX}px, ${currentY}px)`;
    requestAnimationFrame(animate);
}
animate();

mainWrap.addEventListener('mousemove', e => {
    const rect = mainWrap.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    targetX = (x / rect.width - 0.5) * 420;
    targetY = (y / rect.height - 0.5) * 420;
});

mainWrap.addEventListener('mouseleave', () => {
    targetX = 0;
    targetY = 0;
});
// Banner Hover Animation Ends


let btl = gsap.timeline({
    scrollTrigger: {
        trigger: ".tl-wrap",
        start: "0% 100%",
        end: "0% -30%",
        scrub: 0,
        // toggleActions: "play none none reverse"
        // markers: true,
    }
})
btl
    .to(".tl-circle video", {
        width: "100%",
        filter: "blur(0px)",
    }, 0)
    .to(".tl-circle .ring", {
        opacity: 1,
    }, 0)
if (screen.width > 1024) {
    gsap.timeline({
        scrollTrigger: {
            trigger: ".tl-wrap",
            start: "0% 0%",
            end: "100% 0%",
            scrub: 0,
            pin: ".tl-wrap",
            pinType: "fixed",
            // markers: true,
        }
    })

    btl.to(".tl-circle", {
        // top: screen.width > 1200 ? "-95%" : screen.width > 1024 ? "-80%" : "0",
        top: $(".tl-swiper").position().top - 50,
        y: "-100%",
        rotate: 180,
        width: "83%",
    }, 0)
        .to(".main-logo", {
            scale: 0,
            opacity: 0
        }, 0)
} else {
    let splitbt = SplitText.create(".tl-txt p", { type: "words" });
    let btlMob = gsap.timeline({
        scrollTrigger: {
            trigger: ".tl-wrap",
            start: "0% 80%",
            end: "0% 80%",
            toggleActions: "play none none reverse",
            // markers: true
        }
    })
    btlMob.to(".tl-circle", {
        width: screen.width > 576 ? "200%" : "290%",
        top: "-25%",

    }, 0)
        .to(".main-logo", {
            scale: 0,
            opacity: 0,
            duration: 1.2
        }, 0)
        .to(".tl-circle", {
            opacity: 0,
            duration: 1.2
        }, 0)
        .to(".tl-circle .ring", {
            opacity: 1,
            duration: 1.2
        }, 0)
        .from(splitbt.words, {
            y: "50",
            opacity: 0,
            stagger: 0.05,
            duration: 1
        }, 0);

}



// fundamental section
let ftl = gsap.timeline({
    scrollTrigger: {
        trigger: ".fundamental-section",
        start: "0% -40%",
        end: "0% -180%",
        scrub: 0,
        // toggleActions: "play none none reverse"
        // markers: true,
    }
})
if (screen.width > 1024) {

    gsap.timeline({
        scrollTrigger: {
            trigger: ".fundamental-section",
            start: "0% 100%",
            end: "0% 0%",
            scrub: 0,
            // toggleActions: "play none none reverse"
            // preventOverlaps: true
            // markers: true,
        }
    })
        .to(".tl-circle .ring", {
            opacity: 0
        }, 0)
        .to(".tl-circle", {
            scale: 0.7,
            top: "40%",
            rotate: 0,
            y: 0,
        }, 0)


    gsap.timeline({
        scrollTrigger: {
            trigger: ".fundamental-wrap",
            start: "0% 0%",
            end: "0% -200%",
            scrub: 0,
            pin: ".fundamental-wrap",
            pinType: "fixed",
            invalidateOnRefresh: true,
            // markers: true,
        }
    })
    ftl
        .to(".fundamental-head", {
            opacity: 0,
            y: "-100%"
        }, 0)
        .to(".fundamentals-grid ", {
            y: "-20%"
        }, 0)
        .to(".fundamental-wrap .card-txt", {
            opacity: 1,
        }, 0)
        .to(".fundamental-wrap .comm-card", {
            x: 0,
            y: 0
        }, 0)
        .to(".tl-circle", {
            top: screen.width > 1200 ? "-120%" : "-100%",
            rotate: 90
        }, 0)






    // Demo Section
    // gsap.timeline({
    //     scrollTrigger: {
    //         trigger: ".demo-section",
    //         start: "0% 100%",
    //         end: "0% 0%",
    //         scrub: 0,
    //         // preventOverlaps: true
    //         // markers: true,
    //     }
    // })
    //     .to(".tl-circle", {
    //         top: "-170%"
    //     }, 0)


    gsap.timeline({
        scrollTrigger: {
            trigger: ".eng-section",
            start: "0% 80%",
            end: "0% 20%",
            scrub: 0,
            // preventOverlaps: true
            // markers: true,
        }
    })
        .to(".tl-circle .ring", {
            opacity: 1,
            // filter: "blur(200px)"
        }, 0)
        .to(".tl-circle", {
            scale: 0.7,
            top: "-70%",
            // mixBlendMode: "darken"
        }, 0)
    // .to(".tl-circle .circle-overlay", {
    //     scale: 1.1
    // }, 0)
}
else {
    gsap.timeline({
        scrollTrigger: {
            trigger: ".eng-section",
            start: "0% 80%",
            end: "0% 20%",
            scrub: 0,
            // preventOverlaps: true
            // markers: true,
        }
    })

        .to(".tl-circle", {
            width: "90%",
            opacity: 0.2
        }, 0)
        .to(".tl-circle .ring", {
            opacity: 0,
        }, 0)

    gsap.timeline({
        scrollTrigger: {
            trigger: ".success-section",
            start: "0% 80%",
            end: "0% 20%",
            scrub: 0,
            // preventOverlaps: true
            // markers: true,
        }
    })

        .to(".tl-circle", {
            width: "0%",
            opacity: 0
        }, 0)

}


if (screen.width > 1200) {
    // gsap.timeline({
    //     scrollTrigger: {
    //         trigger: ".eng-wrap",
    //         start: "0% 0%",
    //         end: "0% -100%",
    //         scrub: 0,
    //         pin: ".eng-wrap",
    //         pinType: "fixed"
    //     }
    // })
}


if (screen.width > 1024) {

    const canvas1 = document.getElementById('binary-canvas');

    gsap.timeline({
        scrollTrigger: {
            trigger: ".success-section",
            start: "0% 80%",
            end: "0% 0%",
            scrub: 0,
            // markers: true,
        }
    })
        .to(".tl-circle", {
            opacity: 0,
            scale: 0,
            display: "none"
        }, 0)

}

gsap.timeline({
    scrollTrigger: {
        trigger: ".pfHome",
        start: "0% 130%",
        end: "0% 75%",
        scrub: 0,
        // markers: true,
    }
})

    .from(".pf-circle", {
        scale: 0.5
    }, 0)



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

let appList = document.querySelectorAll(".demo-cont li");
$(appList[0]).find("p").slideDown()
appList.forEach((el, index) => {
    el.addEventListener("click", () => {
        appList.forEach((item) => item.classList.remove("active"));
        el.classList.add("active");
        appSwiper.slideTo(index);
    });
});

appSwiper.on("slideChange", () => {
    let index = appSwiper.activeIndex;
    $(".demo-cont li").find("p").slideUp()

    appList.forEach((item, i) => {
        item.classList.toggle("active", i === index);
        $(appList[index]).find("p").slideDown()
    });
});


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
            spaceBetween: 40,
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




// Industry Section Animation
const parent = document.querySelector('.industry-swiper');
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
animate2()
parent.addEventListener('mousemove', e => {
    const rect = parent.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    targetX2 = (x / rect.width - 0.5) * 420;
    targetY2 = (y / rect.height - 0.5) * 420;
});

const industrySwiper = new Swiper('.industrySwiper', {
    slidesPerView: 1,
    speed: 800,
    spaceBetween: 40,
    effect: "fade",
    fadeEffect: {
        crossfade: true
    }
});
var vidInd = document.querySelectorAll(".industry-media video")
vidInd[0].play()
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
    // console.log($(this));
    vidInd[industrySwiper.activeIndex].play()
    $(".industry-acc").eq(industrySwiper.activeIndex).one('transitionend', function () {
        let indTop = $(".industry-acc").eq(industrySwiper.activeIndex).position().top
        gsap.to(".industry-acc-bg", {
            top: indTop,
            duration: 0.3
        })
    })
})




document.querySelectorAll('.hover-card').forEach(parent => {
    const container = parent.querySelector('.lottie');

    const anim = lottie.loadAnimation({
        container: container,
        renderer: 'canvas',
        loop: container.dataset.animLoop === "true",
        autoplay: false,
        path: container.dataset.path
    });

    // Store anim reference on the parent if you need it later
    parent.lottieAnim = anim;

    // Hover on parent, control child animation
    parent.addEventListener('mouseenter', () => anim.play());
    parent.addEventListener('mouseleave', () => anim.stop());
    // If you want pause/resume instead:
    // parent.addEventListener('mouseleave', () => anim.pause());

});


const blueCard = new Swiper('.swiperHomeCards', {
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
        nextEl: '.homeCardNext',
        prevEl: '.homeCardPrev',
    },
    watchSlidesProgress: true
});