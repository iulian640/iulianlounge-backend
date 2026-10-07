package com.iulianlounge.backend.domain;

import java.util.List;

@FunctionalInterface
public interface Shuffler {

    List<Card> shuffle(List<Card> deck);
}
