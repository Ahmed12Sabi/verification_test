package ai.lentra.controller.vehicleDetails;

import ai.lentra.dto.vehicle_info.VehicleDetailsDTO;
import ai.lentra.dto.responses.ResponseDTO;
import ai.lentra.exceptions.DuplicateResourceException;
import ai.lentra.exceptions.InvalidInputException;
import ai.lentra.serviceImpl.vehicleDetails.VehicleDetailsServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@Validated
@RequestMapping(value ="/vehicleDetails")
public class VehicleDetailsController {
    @Autowired
    VehicleDetailsServiceImpl vehicleDetailsService;
    @PostMapping("/vehicles/{applicantId}")
    public ResponseEntity<ResponseDTO> saveVehicleDetails(@RequestBody @Valid VehicleDetailsDTO vehicleDetailsDTO, @PathVariable("applicantId") long applicantId ) throws InvalidInputException, DuplicateResourceException {
        return vehicleDetailsService.saveVehicleDetails(vehicleDetailsDTO,applicantId);
    }
    @GetMapping("/vehicles/{applicantId}")
    public ResponseEntity<Object> getVehicleDetails( @PathVariable Long applicantId )
    {
        return vehicleDetailsService.getVehicleDetails(applicantId);
    }
    @PatchMapping("/vehicles/{applicantId}")
    public ResponseEntity<ResponseDTO> updateVehicleDetails(@RequestBody @Valid VehicleDetailsDTO vehicleDetailsDTO, @PathVariable("applicantId") long applicantId ) throws InvalidInputException {
        return vehicleDetailsService.updateVehicleDetails(vehicleDetailsDTO,applicantId);
    }
}
