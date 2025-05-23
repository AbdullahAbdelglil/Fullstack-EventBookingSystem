package com.eventbooking.service.impl;

import com.eventbooking.service.AuthenticationService;
import com.eventbooking.service.JWTService;
import com.eventbooking.service.UserService;
import com.eventbooking.service.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {

    private final static Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;

    public AuthenticationServiceImpl(UserService userService,
                                     PasswordEncoder passwordEncoder,
                                     AuthenticationManager authenticationManager,
                                     JWTService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public UserDTO register(SignUpRequestDTO signUpRequestDTO) {
        log.debug("signing Up new User UserRequestDTO {}", signUpRequestDTO);
        // Hash the password first
        signUpRequestDTO.setPassword(passwordEncoder.encode(signUpRequestDTO.getPassword()));
        UserDTO userDTO = new UserDTO(signUpRequestDTO);
        return userService.save(userDTO);
    }

    @Override
    public JwtAuthenticationResponse login(SignInRequestDTO signInRequestDTO) {
        log.debug("logging In new User UserEmail {}", signInRequestDTO.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        signInRequestDTO.getEmail(),
                        signInRequestDTO.getPassword())
        );

        UserDTO user = userService.findOne(signInRequestDTO.getEmail()).orElseThrow(
                () -> new IllegalArgumentException("Invalid email or password"));

        String accessToken = jwtService.generateToken(user);
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole());
        String refreshToken = jwtService.generateRefreshToken(claims, user);

        JwtAuthenticationResponse response = new JwtAuthenticationResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }

    @Override
    public JwtAuthenticationResponse refreshToken(RefreshTokenRequestDTO refreshTokenRequestDTO) {
        String refreshToken = refreshTokenRequestDTO.getRefreshToken();
        String userEmail = jwtService.extractUserName(refreshToken);
        UserDTO user = userService.findOne(userEmail).orElseThrow();
        UserDetails userDetails = userService.userDetailsService().loadUserByUsername(user.getEmail());

        if(!jwtService.isTokenValid(refreshToken, userDetails)) {
            return JwtAuthenticationResponse.emptyResponse();
        }

        JwtAuthenticationResponse response = new JwtAuthenticationResponse();
        UserDTO account = userService.findOne(user.getEmail()).orElseThrow();
        String jwtToken = jwtService.generateToken(userDetails, account);
        response.setAccessToken(jwtToken);
        response.setRefreshToken(refreshToken);
        return response;
    }
}
