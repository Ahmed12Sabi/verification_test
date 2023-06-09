package ai.lentra.serviceImpl.reportparsing;

import ai.lentra.dto.ApplicantDetailsDTO;
import ai.lentra.dto.HeadersDTO;
import ai.lentra.dto.commitment.CommitmentDTO;
import ai.lentra.dto.contact_info.ContactDetailsDTO;
import ai.lentra.dto.employment_info.EmploymentDetailsDTO;
import ai.lentra.dto.expenses.ExpensesDTO;
import ai.lentra.dto.famil_info.FamilyDetailsDTO;
import ai.lentra.dto.personal_info.PersonalDetailsDTO;
import ai.lentra.dto.residence.ResidenceDetailsDTO;
import ai.lentra.dto.responses.ResponseDTO;
import ai.lentra.dto.vehicle_info.VehicleDetailsDTO;
import ai.lentra.modal.ApplicantDetails;
import ai.lentra.modal.masterconfig.ReportConfig;
import ai.lentra.modal.masterconfig.ReportConfigFields;
import ai.lentra.modal.summary.Summary;
import ai.lentra.repository.applicantDetails.ApplicantDetailsRepository;
import ai.lentra.repository.masterconfig.ReportConfigFieldsRepository;
import ai.lentra.repository.masterconfig.ReportConfigRepository;
import ai.lentra.service.applicantDetails.ApplicantDetailsService;
import ai.lentra.service.reportparsing.ReportParsingService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReportParsingImpl implements ReportParsingService {
    @Autowired
    private ReportConfigRepository reportConfigRepository;

    @Autowired
    private ApplicantDetailsService applicantDetailsService;
    @Autowired
    private ReportConfigFieldsRepository reportConfigFieldsRepository;

    @Autowired
    private ApplicantDetailsRepository applicantDetailsRepository;
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList("csv", "xlsx");

    @Override
    public ResponseEntity<?> uploadReport(MultipartFile file, Integer institutionId) {

        List<String> errorMessages = new ArrayList<>();
        String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        if (!(ALLOWED_DOCUMENT_TYPES.contains(fileExtension))) {
            errorMessages.add(fileName + " Only Documents allowed with the following extensions "
                    + StringUtils.arrayToCommaDelimitedString(
                    ALLOWED_DOCUMENT_TYPES.toArray(new String[ALLOWED_DOCUMENT_TYPES.size()]))
            );

        }

        if (!errorMessages.isEmpty()) {
            List<ResponseDTO> errros = errorMessages.stream().map(err -> {
                ResponseDTO response = new ResponseDTO();
                response.setCode("400");
                response.setMessage(err);
                response.setStatus("Upload Failed");
                return response;
            }).collect(Collectors.toList());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errros);
        } else {
            if (fileExtension.equals("csv"))
                return saveAllCSV(file, fileName, institutionId);
            else
                return saveAllExcel(file, fileName, institutionId);
//            return ResponseEntity.ok("File has beenuploaded successfully.");
        }

    }

    private ResponseEntity<?> saveAllExcel(MultipartFile file1, String fileName, Integer institutionId) {

        ResponseDTO response = new ResponseDTO();

        try {
            XSSFWorkbook wb = new XSSFWorkbook(file1.getInputStream());
            XSSFSheet sheet = wb.getSheetAt(0);     //creating a Sheet object to retrieve object
            Iterator<Row> itr = sheet.iterator();    //iterating over excel file
            Row headerRow = itr.next();

//            Row headerRow = sheet.getRow(0);
            Iterator<Cell> headerItr = headerRow.cellIterator();
            List<String> headerList = new ArrayList<>();
            while (headerItr.hasNext()) {
                Cell cell = headerItr.next();
                headerList.add(cell.getStringCellValue());
            }
            if (checkValidFile(headerList, institutionId)) {
                response.setCode("400");
                response.setMessage("File Not Valid");
                response.setStatus("");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            while (itr.hasNext()) {
                Row row = itr.next();
                int index = (int) headerList.indexOf("applicantId");

                ApplicantDetails applicantsDetails = applicantDetailsRepository.findByApplicantId((long) row.getCell(index).getNumericCellValue());
                if (applicantsDetails != null) {
                    response.setCode("400");
                    response.setMessage("AppicationId Already exists");
                    response.setStatus("");
//                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {
                    Row row1 = itr.next();
                   return setExceData(row1, headerList);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    private ResponseEntity<ResponseDTO> saveAllCSV(MultipartFile file, String fileName, Integer institutionId) {
        BufferedReader br;
        ResponseDTO response = new ResponseDTO();

        try {

            br = new BufferedReader(new
                    InputStreamReader(file.getInputStream(), "UTF-8"));
            String[] headerString = br.readLine().split(",");
            CSVParser csvParser = new CSVParser(br, CSVFormat.DEFAULT.withSkipHeaderRecord());
            List<CSVRecord> csvRecords = csvParser.getRecords();
            List<String> headerList = new ArrayList<>();
            headerList.addAll(List.of(headerString));
            int appId = headerList.indexOf("applicantId");

            for (CSVRecord csvRecord : csvRecords) {
                ApplicantDetails applicantsDetails = applicantDetailsRepository.findByApplicantId(Long.valueOf(csvRecord.get(appId)));
                if (applicantsDetails != null) {
                    response.setCode("400");
                    response.setMessage("AppicationId Already exists");
                    response.setStatus("");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                } else {

                    if (checkValidFile(headerList, institutionId)) {
                        return saveApplicantDetails(csvRecord, headerList);

                    } else {
                        response.setCode("204");
                        response.setMessage("File not valid");
                        response.setStatus("");
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
                    }

                }

            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private Boolean checkValidFile(List<String> headerList, Integer institutionId) {
        List<ReportConfig> config = reportConfigRepository.findByInstitute(institutionId);
        List<ReportConfigFields> fields = new ArrayList<>();

        config.stream().forEach(c->
                fields.addAll(reportConfigFieldsRepository.findByReportId(c.getId())));
        return fields.stream()
                .allMatch(a -> headerList.stream()
                        .anyMatch(b -> a.getFields().equals(b)
                        ));
    }

    private  ResponseEntity<ResponseDTO>  setExceData(Row row1, List<String> headerList) {
        PersonalDetailsDTO personalDetailsDTO = new PersonalDetailsDTO();
        int index = headerList.indexOf("applicantId");
        int appId= (int) row1.getCell(index).getNumericCellValue();
        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            personalDetailsDTO.setApplicantId((long) row1.getCell(index).getNumericCellValue());
        }
        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            personalDetailsDTO.setApplicantId(Integer.valueOf(row1.getCell(index).getStringCellValue()));
            appId = Integer.valueOf(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("persRefId")) {
            index = headerList.indexOf("persRefId");
            personalDetailsDTO.setPersRefId(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("loanTakenEarlier")) {
            index = headerList.indexOf("loanTakenEarlier");
            personalDetailsDTO.setLoanTakenEarlier(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("citizenship")) {
            index = headerList.indexOf("citizenship");
            personalDetailsDTO.setCitizenship(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("birthDate")) {
            index = headerList.indexOf("birthDate");
            personalDetailsDTO.setBirthDate(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("religion")) {
            index = headerList.indexOf("religion");
            personalDetailsDTO.setReligion(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("martialStatus")) {
            index = headerList.indexOf("martialStatus");
            personalDetailsDTO.setMartialStatus(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("politicallyExposed")) {
            index = headerList.indexOf("politicallyExposed");
            personalDetailsDTO.setPoliticallyExposed(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }

        if (headerList.contains("educationLevel")) {
            index = headerList.indexOf("educationLevel");
            personalDetailsDTO.setEducationLevel(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("lastName")) {
            index = headerList.indexOf("lastName");
            personalDetailsDTO.setLastName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("alias")) {
            index = headerList.indexOf("alias");
            personalDetailsDTO.setAlias(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("middleName")) {
            index = headerList.indexOf("middleName");
            personalDetailsDTO.setMiddleName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("suffix")) {
            index = headerList.indexOf("suffix");
            personalDetailsDTO.setSuffix(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("firstName")) {
            index = headerList.indexOf("firstName");
            personalDetailsDTO.setFirstName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("income")) {
            index = headerList.indexOf("income");
            personalDetailsDTO.setIncome(new BigDecimal(row1.getCell(index).getNumericCellValue()));
        }
        if (headerList.contains("civilStatus")) {
            index = headerList.indexOf("civilStatus");
            personalDetailsDTO.setCivilStatus(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("incomeSource")) {
            index = headerList.indexOf("incomeSource");
            personalDetailsDTO.setIncomeSource(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("incomePeriod")) {
            index = headerList.indexOf("incomePeriod");
            personalDetailsDTO.setIncomePeriod(new BigDecimal(row1.getCell(index).getNumericCellValue()));
        }
        if (headerList.contains("dateTimeEndorsed")) {
            index = headerList.indexOf("dateTimeEndorsed");
            personalDetailsDTO.setDateTimeEndorsed(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("dateInspected")) {
            index = headerList.indexOf("dateInspected");
            personalDetailsDTO.setDateInspected(row1.getCell(index).getStringCellValue());
        }

        //Contact Details
        ContactDetailsDTO contactDetailsDTO = new ContactDetailsDTO();
        contactDetailsDTO.setApplicantId(appId);
        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            contactDetailsDTO.setApplicantId(Long.parseLong(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("mobileNumber")) {
            index = headerList.indexOf("mobileNumber");
            contactDetailsDTO.setMobileNumber(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("personalEmail")) {
            index = headerList.indexOf("personalEmail");
            contactDetailsDTO.setPersonalEmail(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("simType")) {
            index = headerList.indexOf("simType");
            contactDetailsDTO.setSimType(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("mobileNumberVerified")) {
            index = headerList.indexOf("mobileNumberVerified");
            contactDetailsDTO.setMobileNumberVerified(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("phoneNumber")) {
            index = headerList.indexOf("phoneNumber");
            contactDetailsDTO.setPhoneNumber(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("phoneNumberVerified")) {
            index = headerList.indexOf("phoneNumberVerified");
            contactDetailsDTO.setPhoneNumberVerified(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("personalEmailVerified")) {
            index = headerList.indexOf("personalEmailVerified");
            contactDetailsDTO.setPersonalEmailVerified(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("domainCheck")) {
            index = headerList.indexOf("domainCheck");
            contactDetailsDTO.setDomainCheck(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("registeredWithBank")) {
            index = headerList.indexOf("registeredWithBank");
            contactDetailsDTO.setRegisteredWithBank(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        //Expenses details
        ExpensesDTO expensesDto = new ExpensesDTO();
        expensesDto.setApplicantId(appId);

        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            expensesDto.setApplicantId(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("otherExp")) {
            index = headerList.indexOf("otherExp");
            expensesDto.setOtherExpenses((row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("collegeFeesAmt")) {
            index = headerList.indexOf("collegeFeesAmt");
            expensesDto.setCollegeFeesAmt(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }
        if (headerList.contains("schoolFeesAmt")) {
            index = headerList.indexOf("schoolFeesAmt");
            expensesDto.setSchoolFeesAmt(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("electricBillAmt")) {
            index = headerList.indexOf("electricBillAmt");
            expensesDto.setElectricBillAmt(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("officeTransportationCost")) {
            index = headerList.indexOf("officeTransportationCost");
            expensesDto.setOfficeTransportationCost(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }
        if (headerList.contains("cableNetBillAmt")) {
            index = headerList.indexOf("cableNetBillAmt");
            expensesDto.setCableNetBillAmt(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }
        if (headerList.contains("broadbandBillAmt")) {
            index = headerList.indexOf("broadbandBillAmt");
            expensesDto.setBroadbandBillAmt(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }
        if (headerList.contains("avgFuelCost")) {
            index = headerList.indexOf("avgFuelCost");
            expensesDto.setAvgFuelCost(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("waterBillAmt")) {
            index = headerList.indexOf("waterBillAmt");
            expensesDto.setWaterBillAmt(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }

        FamilyDetailsDTO familyDetailsDTO = new FamilyDetailsDTO();
        familyDetailsDTO.setApplicantId(appId);

        if (headerList.contains("numberOfDependents")) {
            index = headerList.indexOf("numberOfDependents");
            familyDetailsDTO.setNumberOfDependents(Integer.parseInt((row1.getCell(index).getStringCellValue())));
        }
        if (headerList.contains("motherMidName")) {
            index = headerList.indexOf("motherMidName");
            familyDetailsDTO.setMotherMidName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("entityConfirmationMother")) {
            index = headerList.indexOf("entityConfirmationMother");
            familyDetailsDTO.setEntityConfirmationMother(Boolean.parseBoolean(((row1.getCell(index).getStringCellValue()))));
        }
        if (headerList.contains("motherAge")) {
            index = headerList.indexOf("motherAge");
            familyDetailsDTO.setMotherAge(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("motherFirstName")) {
            index = headerList.indexOf("motherFirstName");
            familyDetailsDTO.setMotherFirstName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("motherLastName")) {
            index = headerList.indexOf("motherLastName");
            familyDetailsDTO.setMotherLastName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("fatherMidName")) {
            index = headerList.indexOf("fatherMidName");
            familyDetailsDTO.setFatherMidName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("fatherFirstName")) {
            index = headerList.indexOf("fatherFirstName");
            familyDetailsDTO.setFatherFirstName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("fatherAge")) {
            index = headerList.indexOf("fatherAge");
            familyDetailsDTO.setFatherAge(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("fatherReligion")) {
            index = headerList.indexOf("fatherReligion");
            familyDetailsDTO.setFatherReligion(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("fatherLastName")) {
            index = headerList.indexOf("fatherLastName");
            familyDetailsDTO.setFatherLastName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("entityConfirmationFather")) {
            index = headerList.indexOf("entityConfirmationFather");
            familyDetailsDTO.setEntityConfirmationFather(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("numberOfMinor")) {
            index = headerList.indexOf("numberOfMinor");
            familyDetailsDTO.setNumberOfMinor(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("childEducationLevel")) {
            index = headerList.indexOf("childEducationLevel");
            familyDetailsDTO.setChildEducationLevel(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseWorking")) {
            index = headerList.indexOf("spouseWorking");
            familyDetailsDTO.setSpouseWorking(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("spouseLastName")) {
            index = headerList.indexOf("spouseLastName");
            familyDetailsDTO.setSpouseLastName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseOccupation")) {
            index = headerList.indexOf("spouseOccupation");
            familyDetailsDTO.setSpouseOccupation(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseAge")) {
            index = headerList.indexOf("spouseAge");
            familyDetailsDTO.setSpouseAge(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("entityConfirmationSpouse")) {
            index = headerList.indexOf("entityConfirmationSpouse");
            familyDetailsDTO.setEntityConfirmationSpouse(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("spouseReligion")) {
            index = headerList.indexOf("spouseReligion");
            familyDetailsDTO.setSpouseReligion(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseMidName")) {
            index = headerList.indexOf("spouseMidName");
            familyDetailsDTO.setSpouseMidName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseSuffix")) {
            index = headerList.indexOf("spouseSuffix");
            familyDetailsDTO.setSpouseSuffix(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseAlias")) {
            index = headerList.indexOf("spouseAlias");
            familyDetailsDTO.setSpouseAlias(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseBirthdate")) {
            index = headerList.indexOf("spouseBirthdate");
            familyDetailsDTO.setSpouseBirthdate(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("spouseFirstName")) {
            index = headerList.indexOf("spouseFirstName");
            familyDetailsDTO.setSpouseFirstName(row1.getCell(index).getStringCellValue());
        }

        Summary summary = new Summary();
        summary.setApplicantId(appId);
        if (headerList.contains("sumRefId")) {
            index = headerList.indexOf("sumRefId");
            summary.setSumRefId(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("otherFindings")) {
            index = headerList.indexOf("otherFindings");
            summary.setOtherFindings(Integer.valueOf(row1.getCell(index).getStringCellValue()))   ;
        }
        if (headerList.contains("finalScore")) {
            index = headerList.indexOf("finalScore");
            summary.setFinalScore(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("remark")) {
            index = headerList.indexOf("remark");
            summary.setRemark(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("remarkDateTime")) {
            index = headerList.indexOf("remarkDateTime");
            summary.setRemarkDateTime(new Date(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("preparedBy")) {
            index = headerList.indexOf("preparedBy");
            summary.setPreparedBy(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("dateAndTimePerformed")) {
            index = headerList.indexOf("dateAndTimePerformed");
            summary.setDateAndTimePerformed(new Date(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("reviewedBy")) {
            index = headerList.indexOf("reviewedBy");
            summary.setReviewedBy(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("agencyName")) {
            index = headerList.indexOf("agencyName");
            summary.setAgencyName(row1.getCell(index).getStringCellValue());
        }  if (headerList.contains("sumScore")) {
            index = headerList.indexOf("sumScore");
            summary.setSumScore(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }

        //Set Vehicle details Data
        VehicleDetailsDTO vehicleDetails = new VehicleDetailsDTO();
        vehicleDetails.setApplicantId(appId);
        if (headerList.contains("numberOfVehiclesOwned")) {
            index = headerList.indexOf("numberOfVehiclesOwned");
            vehicleDetails.setNumberOfVehiclesOwned(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("bikeRegistrationNumber")) {
            index = headerList.indexOf("bikeRegistrationNumber");
            vehicleDetails.setBikeRegistrationNumber(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("manufactureYearCar")) {
            index = headerList.indexOf("manufactureYearCar");
            vehicleDetails.setManufactureYearCar(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("bikeManufactureName")) {
            index = headerList.indexOf("bikeManufactureName");
            vehicleDetails.setBikeManufactureName((row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("carHypothecatedTo")) {
            index = headerList.indexOf("carHypothecatedTo");
            vehicleDetails.setCarHypothecatedTo((row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("carRegistrationNumber")) {
            index = headerList.indexOf("carRegistrationNumber");
            vehicleDetails.setCarRegistrationNumber(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("withParkingSpace")) {
            index = headerList.indexOf("withParkingSpace");
            vehicleDetails.setWithParkingSpace(Boolean.parseBoolean(row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("carOwnershipType")) {
            index = headerList.indexOf("carOwnershipType");
            vehicleDetails.setCarOwnershipType((row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("manufactureYearTwoWheeler")) {
            index = headerList.indexOf("manufactureYearTwoWheeler");
            vehicleDetails.setManufactureYearTwoWheeler(Integer.valueOf(row1.getCell(index).getStringCellValue()));
        } if (headerList.contains("twoWheelerModel")) {
            index = headerList.indexOf("twoWheelerModel");
            vehicleDetails.setTwoWheelerModel(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("carIssuingAuthority")) {
            index = headerList.indexOf("carIssuingAuthority");
            vehicleDetails.setCarIssuingAuthority(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("carManufactureName")) {
            index = headerList.indexOf("carManufactureName");
            vehicleDetails.setCarManufactureName(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("bikeOwnershipType")) {
            index = headerList.indexOf("bikeOwnershipType");
            vehicleDetails.setBikeOwnershipType(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("financedFromDateCar")) {
            index = headerList.indexOf("financedFromDateCar");
            vehicleDetails.setFinancedFromDateCar(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("carFuelType")) {
            index = headerList.indexOf("carFuelType");
            vehicleDetails.setCarFuelType(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("bikeHypothecatedTo")) {
            index = headerList.indexOf("bikeHypothecatedTo");
            vehicleDetails.setBikeHypothecatedTo(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("carSeatingCapacity")) {
            index = headerList.indexOf("carSeatingCapacity");
            vehicleDetails.setCarSeatingCapacity(Integer.parseInt(row1.getCell(index).getStringCellValue()));
        }
        if (headerList.contains("bikeIssuingAuthority")) {
            index = headerList.indexOf("bikeIssuingAuthority");
            vehicleDetails.setBikeIssuingAuthority(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("vehicleType")) {
            index = headerList.indexOf("vehicleType");
            vehicleDetails.setVehicleType(row1.getCell(index).getStringCellValue());
        }
        if (headerList.contains("carModel")) {
            index = headerList.indexOf("carModel");
            vehicleDetails.setCarModel(row1.getCell(index).getStringCellValue());
        }

        if (headerList.contains("financedFromDateBike")) {
            index = headerList.indexOf("financedFromDateBike");
            vehicleDetails.setFinancedFromDateBike(row1.getCell(index).getStringCellValue());
        }








        CommitmentDTO commitmentDTO = new CommitmentDTO();

        ResidenceDetailsDTO residenceDto = new ResidenceDetailsDTO();
        residenceDto.setApplicant_id(appId);
        EmploymentDetailsDTO officeSelfEmploymentDto = new EmploymentDetailsDTO();
        officeSelfEmploymentDto.setApplicantId(appId);


        ApplicantDetailsDTO applicantDetails = new ApplicantDetailsDTO();
        applicantDetails.setApplicantId(appId);
        applicantDetails.setPersonalDetails(personalDetailsDTO);
        applicantDetails.setContactInformation(contactDetailsDTO);
        applicantDetails.setExpenses(expensesDto);
        applicantDetails.setFamilyDetails(familyDetailsDTO);
        applicantDetails.setSummary(summary);
        applicantDetails.setVehicle(vehicleDetails);
        applicantDetails.setResidences(residenceDto);
        applicantDetails.setCommitments(commitmentDTO);
        applicantDetails.setOfficeSelfEmployment(officeSelfEmploymentDto);
        applicantDetailsService.saveApplicantDetails(Long.valueOf(headerList.indexOf("applicantId")), applicantDetails, new HeadersDTO());
//        applicantDetailsRepository.save(applicantDetails);
        ResponseDTO response = new ResponseDTO();
        response.setData(applicantDetails);
        response.setStatus("200");
        response.setMessage("File Uploaded Successfully");
        response.setCode(HttpStatus.OK.toString());
        return ResponseEntity.status(HttpStatus.OK).body(response);


    }

    private ResponseEntity<ResponseDTO> saveApplicantDetails(CSVRecord csvRecords, List<String> headerList) {
        PersonalDetailsDTO personalDetailsDTO = new PersonalDetailsDTO();
        int index;
        int appId = 0;
        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            personalDetailsDTO.setApplicantId(Integer.valueOf(csvRecords.get(index)));
            appId = Integer.valueOf(csvRecords.get(index));
        }
        if (headerList.contains("persRefId")) {
            index = headerList.indexOf("persRefId");
            personalDetailsDTO.setPersRefId(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("loanTakenEarlier")) {
            index = headerList.indexOf("loanTakenEarlier");
            personalDetailsDTO.setLoanTakenEarlier(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("citizenship")) {
            index = headerList.indexOf("citizenship");
            personalDetailsDTO.setCitizenship(csvRecords.get(index));
        }
        if (headerList.contains("birthDate")) {
            index = headerList.indexOf("birthDate");
            personalDetailsDTO.setBirthDate(csvRecords.get(index));
        }
        if (headerList.contains("religion")) {
            index = headerList.indexOf("religion");
            personalDetailsDTO.setReligion(csvRecords.get(index));
        }
        if (headerList.contains("martialStatus")) {
            index = headerList.indexOf("martialStatus");
            personalDetailsDTO.setMartialStatus(csvRecords.get(index));
        }
        if (headerList.contains("politicallyExposed")) {
            index = headerList.indexOf("politicallyExposed");
            personalDetailsDTO.setPoliticallyExposed(Boolean.parseBoolean(csvRecords.get(index)));
        }

        if (headerList.contains("educationLevel")) {
            index = headerList.indexOf("educationLevel");
            personalDetailsDTO.setEducationLevel(csvRecords.get(index));
        }
        if (headerList.contains("lastName")) {
            index = headerList.indexOf("lastName");
            personalDetailsDTO.setLastName(csvRecords.get(index));
        }
        if (headerList.contains("alias")) {
            index = headerList.indexOf("alias");
            personalDetailsDTO.setAlias(csvRecords.get(index));
        }
        if (headerList.contains("middleName")) {
            index = headerList.indexOf("middleName");
            personalDetailsDTO.setMiddleName(csvRecords.get(index));
        }
        if (headerList.contains("suffix")) {
            index = headerList.indexOf("suffix");
            personalDetailsDTO.setSuffix(csvRecords.get(index));
        }
        if (headerList.contains("firstName")) {
            index = headerList.indexOf("firstName");
            personalDetailsDTO.setFirstName(csvRecords.get(index));
        }
        if (headerList.contains("income")) {
            index = headerList.indexOf("income");
            personalDetailsDTO.setIncome(new BigDecimal(csvRecords.get(index)));
        }
        if (headerList.contains("civilStatus")) {
            index = headerList.indexOf("civilStatus");
            personalDetailsDTO.setCivilStatus(csvRecords.get(index));
        }
        if (headerList.contains("incomeSource")) {
            index = headerList.indexOf("incomeSource");
            personalDetailsDTO.setIncomeSource(csvRecords.get(index));
        }
        if (headerList.contains("incomePeriod")) {
            index = headerList.indexOf("incomePeriod");
            personalDetailsDTO.setIncomePeriod(new BigDecimal(csvRecords.get(index)));
        }
        if (headerList.contains("dateTimeEndorsed")) {
            index = headerList.indexOf("dateTimeEndorsed");
            personalDetailsDTO.setDateTimeEndorsed(csvRecords.get(index));
        }
        if (headerList.contains("dateInspected")) {
            index = headerList.indexOf("dateInspected");
            personalDetailsDTO.setDateInspected(csvRecords.get(index));
        }

        //Contact Details
        ContactDetailsDTO contactDetailsDTO = new ContactDetailsDTO();
        contactDetailsDTO.setApplicantId(appId);
        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            contactDetailsDTO.setApplicantId(Long.parseLong(csvRecords.get(index)));
        }
        if (headerList.contains("mobileNumber")) {
            index = headerList.indexOf("mobileNumber");
            contactDetailsDTO.setMobileNumber(csvRecords.get(index));
        }
        if (headerList.contains("personalEmail")) {
            index = headerList.indexOf("personalEmail");
            contactDetailsDTO.setPersonalEmail(csvRecords.get(index));
        }
        if (headerList.contains("simType")) {
            index = headerList.indexOf("simType");
            contactDetailsDTO.setSimType(csvRecords.get(index));
        }
        if (headerList.contains("mobileNumberVerified")) {
            index = headerList.indexOf("mobileNumberVerified");
            contactDetailsDTO.setMobileNumberVerified(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("phoneNumber")) {
            index = headerList.indexOf("phoneNumber");
            contactDetailsDTO.setPhoneNumber(csvRecords.get(index));
        }
        if (headerList.contains("phoneNumberVerified")) {
            index = headerList.indexOf("phoneNumberVerified");
            contactDetailsDTO.setPhoneNumberVerified(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("personalEmailVerified")) {
            index = headerList.indexOf("personalEmailVerified");
            contactDetailsDTO.setPersonalEmailVerified(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("domainCheck")) {
            index = headerList.indexOf("domainCheck");
            contactDetailsDTO.setDomainCheck(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("registeredWithBank")) {
            index = headerList.indexOf("registeredWithBank");
            contactDetailsDTO.setRegisteredWithBank(Boolean.parseBoolean(csvRecords.get(index)));
        }
        //Expenses details
        ExpensesDTO expensesDto = new ExpensesDTO();
        expensesDto.setApplicantId(appId);

        if (headerList.contains("applicantId")) {
            index = headerList.indexOf("applicantId");
            expensesDto.setApplicantId(Integer.valueOf(csvRecords.get(index)));
        }
        if (headerList.contains("otherExp")) {
            index = headerList.indexOf("otherExp");
            expensesDto.setOtherExpenses((csvRecords.get(index)));
        }
        if (headerList.contains("collegeFeesAmt")) {
            index = headerList.indexOf("collegeFeesAmt");
            expensesDto.setCollegeFeesAmt(Integer.parseInt((csvRecords.get(index))));
        }
        if (headerList.contains("schoolFeesAmt")) {
            index = headerList.indexOf("schoolFeesAmt");
            expensesDto.setSchoolFeesAmt(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("electricBillAmt")) {
            index = headerList.indexOf("electricBillAmt");
            expensesDto.setElectricBillAmt(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("officeTransportationCost")) {
            index = headerList.indexOf("officeTransportationCost");
            expensesDto.setOfficeTransportationCost(Integer.parseInt((csvRecords.get(index))));
        }
        if (headerList.contains("cableNetBillAmt")) {
            index = headerList.indexOf("cableNetBillAmt");
            expensesDto.setCableNetBillAmt(Integer.parseInt((csvRecords.get(index))));
        }
        if (headerList.contains("broadbandBillAmt")) {
            index = headerList.indexOf("broadbandBillAmt");
            expensesDto.setBroadbandBillAmt(Integer.parseInt((csvRecords.get(index))));
        }
        if (headerList.contains("avgFuelCost")) {
            index = headerList.indexOf("avgFuelCost");
            expensesDto.setAvgFuelCost(Integer.valueOf(csvRecords.get(index)));
        }
        if (headerList.contains("waterBillAmt")) {
            index = headerList.indexOf("waterBillAmt");
            expensesDto.setWaterBillAmt(Integer.parseInt((csvRecords.get(index))));
        }

        FamilyDetailsDTO familyDetailsDTO = new FamilyDetailsDTO();
        familyDetailsDTO.setApplicantId(appId);

        if (headerList.contains("numberOfDependents")) {
            index = headerList.indexOf("numberOfDependents");
            familyDetailsDTO.setNumberOfDependents(Integer.parseInt((csvRecords.get(index))));
        }
        if (headerList.contains("motherMidName")) {
            index = headerList.indexOf("motherMidName");
            familyDetailsDTO.setMotherMidName(csvRecords.get(index));
        }
        if (headerList.contains("entityConfirmationMother")) {
            index = headerList.indexOf("entityConfirmationMother");
            familyDetailsDTO.setEntityConfirmationMother(Boolean.parseBoolean(((csvRecords.get(index)))));
        }
        if (headerList.contains("motherAge")) {
            index = headerList.indexOf("motherAge");
            familyDetailsDTO.setMotherAge(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("motherFirstName")) {
            index = headerList.indexOf("motherFirstName");
            familyDetailsDTO.setMotherFirstName(csvRecords.get(index));
        }
        if (headerList.contains("motherLastName")) {
            index = headerList.indexOf("motherLastName");
            familyDetailsDTO.setMotherLastName(csvRecords.get(index));
        }
        if (headerList.contains("fatherMidName")) {
            index = headerList.indexOf("fatherMidName");
            familyDetailsDTO.setFatherMidName(csvRecords.get(index));
        }
        if (headerList.contains("fatherFirstName")) {
            index = headerList.indexOf("fatherFirstName");
            familyDetailsDTO.setFatherFirstName(csvRecords.get(index));
        }
        if (headerList.contains("fatherAge")) {
            index = headerList.indexOf("fatherAge");
            familyDetailsDTO.setFatherAge(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("fatherReligion")) {
            index = headerList.indexOf("fatherReligion");
            familyDetailsDTO.setFatherReligion(csvRecords.get(index));
        }
        if (headerList.contains("fatherLastName")) {
            index = headerList.indexOf("fatherLastName");
            familyDetailsDTO.setFatherLastName(csvRecords.get(index));
        }
        if (headerList.contains("entityConfirmationFather")) {
            index = headerList.indexOf("entityConfirmationFather");
            familyDetailsDTO.setEntityConfirmationFather(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("numberOfMinor")) {
            index = headerList.indexOf("numberOfMinor");
            familyDetailsDTO.setNumberOfMinor(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("childEducationLevel")) {
            index = headerList.indexOf("childEducationLevel");
            familyDetailsDTO.setChildEducationLevel(csvRecords.get(index));
        }
        if (headerList.contains("spouseWorking")) {
            index = headerList.indexOf("spouseWorking");
            familyDetailsDTO.setSpouseWorking(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("spouseLastName")) {
            index = headerList.indexOf("spouseLastName");
            familyDetailsDTO.setSpouseLastName(csvRecords.get(index));
        }
        if (headerList.contains("spouseOccupation")) {
            index = headerList.indexOf("spouseOccupation");
            familyDetailsDTO.setSpouseOccupation(csvRecords.get(index));
        }
        if (headerList.contains("spouseAge")) {
            index = headerList.indexOf("spouseAge");
            familyDetailsDTO.setSpouseAge(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("entityConfirmationSpouse")) {
            index = headerList.indexOf("entityConfirmationSpouse");
            familyDetailsDTO.setEntityConfirmationSpouse(Boolean.parseBoolean(csvRecords.get(index)));
        }
        if (headerList.contains("spouseReligion")) {
            index = headerList.indexOf("spouseReligion");
            familyDetailsDTO.setSpouseReligion(csvRecords.get(index));
        }
        if (headerList.contains("spouseMidName")) {
            index = headerList.indexOf("spouseMidName");
            familyDetailsDTO.setSpouseMidName(csvRecords.get(index));
        }
        if (headerList.contains("spouseSuffix")) {
            index = headerList.indexOf("spouseSuffix");
            familyDetailsDTO.setSpouseSuffix(csvRecords.get(index));
        }
        if (headerList.contains("spouseAlias")) {
            index = headerList.indexOf("spouseAlias");
            familyDetailsDTO.setSpouseAlias(csvRecords.get(index));
        }
        if (headerList.contains("spouseBirthdate")) {
            index = headerList.indexOf("spouseBirthdate");
            familyDetailsDTO.setSpouseBirthdate(csvRecords.get(index));
        }
        if (headerList.contains("spouseFirstName")) {
            index = headerList.indexOf("spouseFirstName");
            familyDetailsDTO.setSpouseFirstName(csvRecords.get(index));
        }

        Summary summary = new Summary();
        summary.setApplicantId(appId);
        if (headerList.contains("sumRefId")) {
            index = headerList.indexOf("sumRefId");
            summary.setSumRefId(Integer.valueOf(csvRecords.get(index)));
        }
        if (headerList.contains("otherFindings")) {
            index = headerList.indexOf("otherFindings");
            summary.setOtherFindings(Integer.valueOf(csvRecords.get(index)))   ;
        }
        if (headerList.contains("finalScore")) {
            index = headerList.indexOf("finalScore");
            summary.setFinalScore(Integer.valueOf(csvRecords.get(index)));
        }
        if (headerList.contains("remark")) {
            index = headerList.indexOf("remark");
            summary.setRemark(csvRecords.get(index));
        }
        if (headerList.contains("remarkDateTime")) {
            index = headerList.indexOf("remarkDateTime");
            summary.setRemarkDateTime(new Date(csvRecords.get(index)));
        }
        if (headerList.contains("preparedBy")) {
            index = headerList.indexOf("preparedBy");
            summary.setPreparedBy(csvRecords.get(index));
        }
        if (headerList.contains("dateAndTimePerformed")) {
            index = headerList.indexOf("dateAndTimePerformed");
            summary.setDateAndTimePerformed(new Date(csvRecords.get(index)));
        }
        if (headerList.contains("reviewedBy")) {
            index = headerList.indexOf("reviewedBy");
            summary.setReviewedBy(csvRecords.get(index));
        }
        if (headerList.contains("agencyName")) {
            index = headerList.indexOf("agencyName");
            summary.setAgencyName(csvRecords.get(index));
        }  if (headerList.contains("sumScore")) {
            index = headerList.indexOf("sumScore");
            summary.setSumScore(Integer.valueOf(csvRecords.get(index)));
        }

        //Set Vehicle details Data
        VehicleDetailsDTO vehicleDetails = new VehicleDetailsDTO();
        vehicleDetails.setApplicantId(appId);
        if (headerList.contains("numberOfVehiclesOwned")) {
            index = headerList.indexOf("numberOfVehiclesOwned");
            vehicleDetails.setNumberOfVehiclesOwned(Integer.valueOf(csvRecords.get(index)));
        }
        if (headerList.contains("bikeRegistrationNumber")) {
            index = headerList.indexOf("bikeRegistrationNumber");
            vehicleDetails.setBikeRegistrationNumber(Integer.valueOf(csvRecords.get(index)));
        } if (headerList.contains("manufactureYearCar")) {
            index = headerList.indexOf("manufactureYearCar");
            vehicleDetails.setManufactureYearCar(Integer.valueOf(csvRecords.get(index)));
        } if (headerList.contains("bikeManufactureName")) {
            index = headerList.indexOf("bikeManufactureName");
            vehicleDetails.setBikeManufactureName((csvRecords.get(index)));
        } if (headerList.contains("carHypothecatedTo")) {
            index = headerList.indexOf("carHypothecatedTo");
            vehicleDetails.setCarHypothecatedTo((csvRecords.get(index)));
        } if (headerList.contains("carRegistrationNumber")) {
            index = headerList.indexOf("carRegistrationNumber");
            vehicleDetails.setCarRegistrationNumber(Integer.valueOf(csvRecords.get(index)));
        } if (headerList.contains("withParkingSpace")) {
            index = headerList.indexOf("withParkingSpace");
            vehicleDetails.setWithParkingSpace(Boolean.parseBoolean(csvRecords.get(index)));
        } if (headerList.contains("carOwnershipType")) {
            index = headerList.indexOf("carOwnershipType");
            vehicleDetails.setCarOwnershipType((csvRecords.get(index)));
        } if (headerList.contains("manufactureYearTwoWheeler")) {
            index = headerList.indexOf("manufactureYearTwoWheeler");
            vehicleDetails.setManufactureYearTwoWheeler(Integer.valueOf(csvRecords.get(index)));
        } if (headerList.contains("twoWheelerModel")) {
            index = headerList.indexOf("twoWheelerModel");
            vehicleDetails.setTwoWheelerModel(csvRecords.get(index));
        }
        if (headerList.contains("carIssuingAuthority")) {
            index = headerList.indexOf("carIssuingAuthority");
            vehicleDetails.setCarIssuingAuthority(csvRecords.get(index));
        }
        if (headerList.contains("carManufactureName")) {
            index = headerList.indexOf("carManufactureName");
            vehicleDetails.setCarManufactureName(csvRecords.get(index));
        }
        if (headerList.contains("bikeOwnershipType")) {
            index = headerList.indexOf("bikeOwnershipType");
            vehicleDetails.setBikeOwnershipType(csvRecords.get(index));
        }
        if (headerList.contains("financedFromDateCar")) {
            index = headerList.indexOf("financedFromDateCar");
            vehicleDetails.setFinancedFromDateCar(csvRecords.get(index));
        }
        if (headerList.contains("carFuelType")) {
            index = headerList.indexOf("carFuelType");
            vehicleDetails.setCarFuelType(csvRecords.get(index));
        }
        if (headerList.contains("bikeHypothecatedTo")) {
            index = headerList.indexOf("bikeHypothecatedTo");
            vehicleDetails.setBikeHypothecatedTo(csvRecords.get(index));
        }
        if (headerList.contains("carSeatingCapacity")) {
            index = headerList.indexOf("carSeatingCapacity");
            vehicleDetails.setCarSeatingCapacity(Integer.parseInt(csvRecords.get(index)));
        }
        if (headerList.contains("bikeIssuingAuthority")) {
            index = headerList.indexOf("bikeIssuingAuthority");
            vehicleDetails.setBikeIssuingAuthority(csvRecords.get(index));
        }
        if (headerList.contains("vehicleType")) {
            index = headerList.indexOf("vehicleType");
            vehicleDetails.setVehicleType(csvRecords.get(index));
        }
        if (headerList.contains("carModel")) {
            index = headerList.indexOf("carModel");
            vehicleDetails.setCarModel(csvRecords.get(index));
        }

        if (headerList.contains("financedFromDateBike")) {
            index = headerList.indexOf("financedFromDateBike");
            vehicleDetails.setFinancedFromDateBike(csvRecords.get(index));
        }








        CommitmentDTO commitmentDTO = new CommitmentDTO();

        ResidenceDetailsDTO residenceDto = new ResidenceDetailsDTO();
        residenceDto.setApplicant_id(appId);
        EmploymentDetailsDTO officeSelfEmploymentDto = new EmploymentDetailsDTO();
        officeSelfEmploymentDto.setApplicantId(appId);


        ApplicantDetailsDTO applicantDetails = new ApplicantDetailsDTO();
        applicantDetails.setApplicantId(appId);
        applicantDetails.setPersonalDetails(personalDetailsDTO);
        applicantDetails.setContactInformation(contactDetailsDTO);
        applicantDetails.setExpenses(expensesDto);
        applicantDetails.setFamilyDetails(familyDetailsDTO);
        applicantDetails.setSummary(summary);
        applicantDetails.setVehicle(vehicleDetails);
        applicantDetails.setResidences(residenceDto);
        applicantDetails.setCommitments(commitmentDTO);
        applicantDetails.setOfficeSelfEmployment(officeSelfEmploymentDto);
        applicantDetailsService.saveApplicantDetails(Long.valueOf(headerList.indexOf("applicantId")), applicantDetails, new HeadersDTO());
//        applicantDetailsRepository.save(applicantDetails);
        ResponseDTO response = new ResponseDTO();
        response.setData(applicantDetails);
        response.setStatus("200");
        response.setMessage("File Uploaded Successfully");
        response.setCode(HttpStatus.OK.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

}
