package mtech.swe5006.peerconnect.security;

import mtech.swe5006.peerconnect.data.sql.User;
import mtech.swe5006.peerconnect.data.sql.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())          // <- email is the login username
            .password(user.getPasswordHash())       // <- hashed password from DB
            .authorities("ROLE_USER")
            .build();
    }
}
