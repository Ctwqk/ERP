package com.example.myerp.security;

import com.example.myerp.domain.AppUser;
import com.example.myerp.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public AppUserDetailService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser u = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().toString()));

        return new AuthUser(u.getId(), u.getEmail(), u.getPasswordHash(), authorities);
    }
}
