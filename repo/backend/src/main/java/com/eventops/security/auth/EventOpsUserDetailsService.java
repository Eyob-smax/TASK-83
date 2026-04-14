package com.eventops.security.auth;

import com.eventops.domain.user.User;
import com.eventops.repository.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads {@link EventOpsUserDetails} from the database for Spring Security's
 * authentication pipeline.
 */
@Service
public class EventOpsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public EventOpsUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Locates the user by username.
     *
     * @param username the username identifying the user whose data is required
     * @return a fully populated {@link EventOpsUserDetails} instance
     * @throws UsernameNotFoundException if no user exists with the given username
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username: " + username));
        return new EventOpsUserDetails(user);
    }
}
