package com.iulianlounge.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.TransactionType;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Test
    void getProfileReturnsUserDataWithStartingRank() {
        User user = cursaito();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(walletService.spentOn(user.getId(), TransactionType.BAR_ORDER)).thenReturn(0L);

        MeResponse profile = new UserService(userRepository, walletService).getProfile(user.getId());

        assertEquals(user.getId(), profile.userId());
        assertEquals("cursaito", profile.username());
        assertEquals("es", profile.locale());
        assertEquals(Rank.NADIE, profile.rank());
    }

    @Test
    void getProfileDerivesTheRankFromWhatWasSpentAtTheBar() {
        User user = cursaito();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(walletService.spentOn(user.getId(), TransactionType.BAR_ORDER)).thenReturn(30L);

        MeResponse profile = new UserService(userRepository, walletService).getProfile(user.getId());

        assertEquals(Rank.HABITUAL, profile.rank());
    }

    @Test
    void getProfileOfDeletedAccountIsAnInvalidToken() {
        UUID deleted = UUID.randomUUID();
        when(userRepository.findById(deleted)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> new UserService(userRepository, walletService).getProfile(deleted));
    }

    private static User cursaito() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setLocale(Language.ES);
        return user;
    }
}
