package com.polytech.knowledgebase.structure.repository;
import com.polytech.knowledgebase.structure.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface EvidenceRepository extends JpaRepository<Evidence, String> {
    boolean existsBySourceId(String sourceId);
}
