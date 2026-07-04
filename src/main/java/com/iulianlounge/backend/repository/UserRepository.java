package com.iulianlounge.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.iulianlounge.backend.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    
} 
