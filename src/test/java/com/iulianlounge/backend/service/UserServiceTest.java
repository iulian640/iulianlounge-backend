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

import com.iulianlounge.backend.domain.Language;
import com.iulianlounge.backend.domain.Rank;
import com.iulianlounge.backend.domain.User;
import com.iulianlounge.backend.dto.MeResponse;
import com.iulianlounge.backend.exception.InvalidTokenException;
import com.iulianlounge.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BarService barService;

    @Test
    void getProfileReturnsUserDataWithItsRank() {
        User user = cursaito();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(barService.rankOf(user.getId())).thenReturn(Rank.HABITUAL);

        MeResponse profile = new UserService(userRepository, barService).getProfile(user.getId());

        assertEquals(user.getId(), profile.userId());
        assertEquals("cursaito", profile.username());
        assertEquals("es", profile.locale());
        assertEquals(Rank.HABITUAL, profile.rank());
    }

    @Test
    void getProfileOfDeletedAccountIsAnInvalidToken() {
        UUID deleted = UUID.randomUUID();
        when(userRepository.findById(deleted)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class,
                () -> new UserService(userRepository, barService).getProfile(deleted));
    }

    private static User cursaito() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cursaito");
        user.setLocale(Language.ES);
        return user;
    }
}
