package com.tvasw.backend.service;

import com.tvasw.backend.dto.*;
import com.tvasw.backend.model.User;
import com.tvasw.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final String dummyHash;

    public AuthService(UserRepository userRepo, PasswordEncoder encoder, JwtService jwtService) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.dummyHash = encoder.encode("unused");
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public AuthResponse register(RegisterRequest req) {
        // BCrypt only accepts 72 bytes, which is fewer characters for non-ASCII passwords
        if (req.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long");
        }
        String email = normalize(req.getEmail());
        ResponseStatusException taken = new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        if (userRepo.findByEmail(email).isPresent()) throw taken;
        try {
            return toResponse(userRepo.save(new User(email, req.getName().trim(), encoder.encode(req.getPassword()))));
        } catch (DataIntegrityViolationException ex) {
            throw taken;
        }
    }

    public AuthResponse login(LoginRequest req) {
        Optional<User> user = userRepo.findByEmail(normalize(req.getEmail()));
        // Always compare a hash, so response time doesn't reveal which emails exist
        boolean matches = encoder.matches(req.getPassword(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        return toResponse(user.get());
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.createToken(user.getId()), user.getName(), user.getEmail());
    }
}
