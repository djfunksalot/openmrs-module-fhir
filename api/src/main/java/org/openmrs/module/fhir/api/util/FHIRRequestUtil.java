package org.openmrs.module.fhir.api.util;

import org.hl7.fhir.dstu3.model.BackboneElement;
import org.hl7.fhir.dstu3.model.DomainResource;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.hl7.fhir.dstu3.model.ProcedureRequest;
import org.hl7.fhir.dstu3.model.Reference;
import org.openmrs.CareSetting;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir.api.constants.ExtensionURL;

import java.util.List;

public final class FHIRRequestUtil {

    private static final int INPATIENT = 2;
    private static final int FIRST = 0;

    private static final String REQUEST_INSTANCE_ERROR_MSG = "FHIR Resource should be instance of MedicationRequest or ProcedureRequest";
    private static final String EMPTY_REQUESTER_ERROR_MSG = "Requester cannot be empty";

    private FHIRRequestUtil() { }

    //region FHIR methods

    public static Extension buildCareSettingExtension(Order omrsOrder) {
        CareSetting careSetting = omrsOrder.getCareSetting();
        if (careSetting != null) {
            return ExtensionsUtil.createCareSettingExtension(careSetting.getUuid());
        }
        return null;
    }

    public static Reference buildSubject(Order omrsOrder) {
        Patient patient = omrsOrder.getPatient();
        return FHIRPatientUtil.buildPatientReference(patient);
    }

    public static Provider buildOrderer(DomainResource fhirRequest, List<String> errors) {
        if (!validateInstanceType(fhirRequest, errors)) {
            return null;
        }

        Reference providerRef = getRequesterRef(fhirRequest, errors);
        if (providerRef == null) {
            return null;
        }
        String providerUuid =  FHIRUtils.getObjectUuidByReference(providerRef);
        return Context.getProviderService().getProviderByUuid(providerUuid);
    }

    private static Reference getRequesterRef(DomainResource fhirRequest, List<String> errors) {
        BackboneElement requester = null;
        if (fhirRequest instanceof MedicationRequest) {
            requester = ((MedicationRequest) fhirRequest).getRequester();
        } else if (fhirRequest instanceof ProcedureRequest){
            requester = ((ProcedureRequest) fhirRequest).getRequester();
        }

        if (requester == null) {
            errors.add(EMPTY_REQUESTER_ERROR_MSG);
            return null;
        }

        if (requester instanceof MedicationRequest.MedicationRequestRequesterComponent) {
            return ((MedicationRequest.MedicationRequestRequesterComponent) requester).getAgent();
        } else {
            return ((ProcedureRequest.ProcedureRequestRequesterComponent) requester).getAgent();
        }
    }

    //endregion

    //region OpenMRS methods

    public static CareSetting buildCareSetting(DomainResource fhirRequest, List<String> errors) {
        if (!validateInstanceType(fhirRequest, errors)) {
            return null;
        }
        List<Extension> extensions = fhirRequest.getExtensionsByUrl(ExtensionURL.CARE_SETTING);
        if (extensions.size() > 0) {
            String careSettingUuid = ExtensionsUtil.getStringFromExtension(extensions.get(FIRST));
            CareSetting careSetting = Context.getOrderService().getCareSettingByUuid(careSettingUuid);
            if (careSetting != null) {
                return careSetting;
            }
        }
        return Context.getOrderService().getCareSetting(INPATIENT);
    }

    public static Patient buildPatient(DomainResource fhirRequest, List<String> errors) {
        if (!validateInstanceType(fhirRequest, errors)) {
            return null;
        }

        Patient patient = null;
        Reference patientRef;

        if (fhirRequest instanceof MedicationRequest) {
            patientRef = ((MedicationRequest) fhirRequest).getSubject();
        } else {
            patientRef = ((ProcedureRequest) fhirRequest).getSubject();
        }

        if (patientRef != null) {
            String patientUuid = FHIRUtils.getObjectUuidByReference(patientRef);
            patient = Context.getPatientService().getPatientByUuid(patientUuid);
            if (patient == null) {
                errors.add("There is no patient for the given uuid");
            }
        } else {
            errors.add("Subject cannot be empty");
        }
        return patient;
    }

    public static Reference buildPractitionerReference(Order omrsOrder) {
        Provider provider = omrsOrder.getOrderer();
        if (provider != null) {
            return FHIRPractitionerUtil.buildPractionaerReference(provider);
        }
        return null;
    }

    public static Reference buildContext(Order omrsOrder) {
        Encounter encounter = omrsOrder.getEncounter();
        if (encounter != null) {
            Reference encounterRef = FHIRObsUtil.getFHIREncounterReference(encounter);
            encounterRef.setId(encounter.getUuid());
            return encounterRef;
        }
        return null;
    }

    //endregion

    private static boolean validateInstanceType(DomainResource fhirDomainResource, List<String> errors) {
        if (fhirDomainResource instanceof MedicationRequest || fhirDomainResource instanceof ProcedureRequest) {
            return true;
        }
        errors.add(REQUEST_INSTANCE_ERROR_MSG);
        return false;
    }
}
