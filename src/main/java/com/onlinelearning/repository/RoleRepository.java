package com.onlinelearning.repository;
import com.onlinelearning.entity.Role; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RoleRepository extends JpaRepository<Role,Long> { Optional<Role> findByCode(String code); }
