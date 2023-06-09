package ai.lentra.dto;

import ai.lentra.dto.commitment.CommitmentDTO;
import ai.lentra.dto.contact_info.ContactDetailsDTO;
import ai.lentra.dto.employment_info.EmploymentDetailsDTO;
import ai.lentra.dto.expenses.ExpensesDTO;
import ai.lentra.dto.famil_info.FamilyDetailsDTO;
import ai.lentra.dto.personal_info.PersonalDetailsDTO;
import ai.lentra.dto.residence.ResidenceDetailsDTO;
import ai.lentra.dto.summary.SummaryDTO;
import ai.lentra.modal.vehicle_info.VehicleDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicantDetailsHistoryDTO {
    private long applicantId;
    private SummaryDTO summary;
    private CommitmentDTO commitments;
    private FamilyDetailsDTO familyDetails;
    private EmploymentDetailsDTO employmentDetails;
    private ContactDetailsDTO contactInformation;
    private PersonalDetailsDTO personalDetails;
    private ResidenceDetailsDTO residences;
    private ExpensesDTO expenses;
    private VehicleDetails vehicleDetails;
}
