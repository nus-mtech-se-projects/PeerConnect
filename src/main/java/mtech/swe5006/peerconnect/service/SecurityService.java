package mtech.swe5006.peerconnect.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class SecurityService {

    private final UserRepository userRepository;

    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or missing JWT");
        }

        User user = userRepository.findByEmail(authentication.getName()).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid or missing JWT");
        }
        return user.getId();
    }
}
