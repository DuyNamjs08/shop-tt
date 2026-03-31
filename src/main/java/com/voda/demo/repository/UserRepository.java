package com.voda.demo.repository;

import com.voda.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // Không cần viết gì cả!
    // JpaRepository đã có sẵn: findAll, findById, save, deleteById...
}
