package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.*;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.UserRole;
import com.example.HotelBooking.exceptions.InvalidCredentialException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.UserRepository;
import com.example.HotelBooking.security.JwtUtils;
import com.example.HotelBooking.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ModelMapper modelMapper;
    private final BookingRepository bookingRepository;
    private final JavaMailSender javaMailSender;
    private final RedisTemplate<String, String> redisTemplate;
    private static final String OTP_PREFIX = "OTP_";


    @Override
    public Response registerUser(RegistrationRequest registrationRequest) {
        UserRole role = UserRole.CUSTOMER;

        if (registrationRequest.getRole() != null) {
            role = registrationRequest.getRole();
        }

        User userToSave = User.builder()
            .firstName(registrationRequest.getFirstName())
            .lastName(registrationRequest.getLastName())
            .email(registrationRequest.getEmail())
            .password(passwordEncoder.encode(registrationRequest.getPassword()))
            .phoneNumber(registrationRequest.getPhoneNumber())
            .role(role)
            .isActive(Boolean.TRUE)
            .build();

        userRepository.save(userToSave);

        return Response.builder()
                .status(200)
                .message("user created successfully")
                .build();

    }

    @Override
    public Response loginUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(()-> new NotFoundException("Email Not Found"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new InvalidCredentialException("Password doesn't match");
        }

        String token = jwtUtils.generateToken(user.getEmail());


        return Response.builder()
                .status(200)
                .message("user logged in successfully")
                .role(user.getRole())
                .token(token)
                .isActive(user.getIsActive())
                .expirationTime("6 months")
                .build();
    }

    @Override
    public Response getAllUsers() {
        List<User> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));

        List<UserDTO> userDTOList = modelMapper.map(users, new TypeToken<List<UserDTO>>(){}.getType());


        return Response.builder()
                .status(200)
                .message("success")
                .users(userDTOList)
                .build();
    }

    @Override
    public Response getOwnAccountDetails() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(()-> new NotFoundException("User Not Found"));


        log.info("Inside getOwnAccountDetails user email is {}", email);

        UserDTO userDTO = modelMapper.map(user, UserDTO.class);

        return Response.builder()
                .status(200)
                .message("success")
                .user(userDTO)
                .build();
    }

    @Override
    public User getCurrentLoggedInUser() {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(()-> new NotFoundException("User Not Found"));
    }

    @Override
    public Response updateOwnAccount(UserDTO userDTO) {
        User existingUser = getCurrentLoggedInUser();
        log.info("Inside update user");

        if (userDTO.getEmail() != null) existingUser.setEmail(userDTO.getEmail());
        if (userDTO.getFirstName() != null) existingUser.setFirstName(userDTO.getFirstName());
        if (userDTO.getLastName() != null) existingUser.setLastName(userDTO.getLastName());
        if (userDTO.getPhoneNumber() != null) existingUser.setPhoneNumber(userDTO.getPhoneNumber());

        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(existingUser);

        return Response.builder()
                .status(200)
                .message("user updated successfully")
                .build();
    }

    @Override
    public Response deleteOwnAccount() {
        User user = getCurrentLoggedInUser();
        userRepository.delete(user);

        return Response.builder()
                .status(200)
                .message("user deleted successfully")
                .build();
    }

    @Override
    public Response getMyBookingHistory() {

        User user = getCurrentLoggedInUser();

        List<Booking> bookingList = bookingRepository.findByUserId(user.getId());


        List<BookingDTO> bookingDTOList = modelMapper.map(bookingList, new TypeToken<List<BookingDTO>>(){}.getType());

        return Response.builder()
                .status(200)
                .message("success")
                .bookings(bookingDTOList)
                .build();

    }


    // For Password Forget then Resetting Password


    @Override
    public Response sendOtp(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);
        if(optionalUser.isEmpty()) {
            throw new NotFoundException("No user found with this email");
        }

        // 6 digit Otp Generating
        String otp = String.format("%06d", new Random().nextInt(999999));

        // Save OTP in Redis with 5-minute expiry
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp, Duration.ofMinutes(5));

        sendEmailOtp(email, otp);

        return Response.builder()
                .status(200)
                .message("OTP send to register email")
                .build();
    }

    private void sendEmailOtp(String email, String otp) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(email);
        mail.setSubject("Password Reset OTP - HotelBooking");
        mail.setText("Your OTP for password reset is: " + otp + "\n\nValid for 5 minutes only.");
        javaMailSender.send(mail);
    }

    @Override
    public Response verifyOtpAndResetPassword(String email, String otp, String newPassword) {
        String storedOtp = redisTemplate.opsForValue().get(OTP_PREFIX + email);



        if(storedOtp == null) {
            throw new InvalidCredentialException("OTP expired or not found");
        }

        if(!storedOtp.equals(otp)) {
            throw new InvalidCredentialException("Invalid OTP");
        }

        // update OTP
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

//         Delete OTP after use
        redisTemplate.delete(OTP_PREFIX + email);

        return Response.builder()
                .status(200)
                .message("Password updated successfully")
                .build();
    }






}

