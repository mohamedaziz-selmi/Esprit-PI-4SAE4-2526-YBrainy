package com.esprit.userservice.model;

public enum XpSource {
    THREAD_CREATED(20),
    POST_CREATED(10),
    POST_DETAILED(5),
    COMMENT_CREATED(5),
    UPVOTE_RECEIVED(3),
    BEST_ANSWER(50);

    private final int amount;

    XpSource(int amount) {
        this.amount = amount;
    }

    public int getAmount() {
        return amount;
    }
}
