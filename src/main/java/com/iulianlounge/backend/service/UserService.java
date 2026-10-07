package com.iulianlounge.backend.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;

    public UserService(UserRepository userRepository, WalletService walletService) {
        this.userRepository = userRepository;
        this.walletService = walletService;
    }

    public MeResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException());
        Rank rank = Rank.forSpent(walletService.spentOn(userId, TransactionType.BAR_ORDER));

        return new MeResponse(user.getId(), user.getUsername(), user.getLocale().code(), rank);
    }
}
