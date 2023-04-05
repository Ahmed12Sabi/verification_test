package ai.lentra.repository.personal_details;

import ai.lentra.modal.personal_info.PersonalDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface PersonalDetailsRepository extends JpaRepository<PersonalDetails, Long> {
    Optional<PersonalDetails> findByApplicantId(long applicantId);

    boolean deleteByApplicantId(long i);
}