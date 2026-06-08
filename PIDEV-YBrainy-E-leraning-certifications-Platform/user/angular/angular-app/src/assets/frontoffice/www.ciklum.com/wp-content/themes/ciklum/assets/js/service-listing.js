document.querySelectorAll('.sl-swiper-wrap').forEach((wrap) => {
    const sliderEl = wrap.querySelector('.slSwiper');
    const nextBtn = wrap.querySelector('.sl-next');
    const prevBtn = wrap.querySelector('.sl-prev');

    new Swiper(sliderEl, {
        slidesPerView: 1,
        speed: 800,
        spaceBetween: 10,

        navigation: {
            nextEl: nextBtn,
            prevEl: prevBtn,
        },

        breakpoints: {
            577: {
                slidesPerView: 3,
                spaceBetween: 20,
            },
            801: {
                slidesPerView: 4,
                spaceBetween: 20,
            },
            1025: {
                slidesPerView: 4,
                spaceBetween: 50,
            },
            1367: {
                slidesPerView: 3,
                spaceBetween: 60,
            },
        },
    });
});
