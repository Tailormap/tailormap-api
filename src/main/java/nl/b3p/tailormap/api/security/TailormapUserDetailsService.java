package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.repository.UserRepository;
import nl.tailormap.viewer.config.security.User;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class TailormapUserDetailsService implements UserDetailsService {
    private final Log logger = LogFactory.getLog(getClass());

    @Autowired private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User " + username + " not found");
        }
        logger.trace("Found user: " + user.getUsername() + ", password " + user.getPassword());
        return new TailormapUserDetails(user);
    }
}
