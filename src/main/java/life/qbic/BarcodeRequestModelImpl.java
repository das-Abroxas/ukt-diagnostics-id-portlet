package life.qbic;

import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import life.qbic.helpers.BarcodeFunctions;
import life.qbic.helpers.Utils;
import life.qbic.openbis.openbisclient.OpenBisClient;

import java.util.*;
import java.util.stream.Collectors;

import static life.qbic.helpers.BarcodeFunctions.checksum;

public class BarcodeRequestModelImpl implements BarcodeRequestModel{

    private final OpenBisClient openBisClient;

    private final static String SPACE = "UKT_DIAGNOSTICS";

    private final static String PROJECTID = "/UKT_DIAGNOSTICS/QUK17";

    private final static String CODE = "QUK17";

    private static Log log = LogFactoryUtil.getLog(MyPortletUI.class);

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVW".toCharArray();

    private static String[] patientSampleIdPair= null;

    public BarcodeRequestModelImpl(OpenBisClient openBisClient){
        this.openBisClient = openBisClient;
    }

    @Override
    public void requestNewPatientSampleIdPair() {
        patientSampleIdPair = new String[2];

        int[] sizes = getNumberOfSampleTypes();

        // offset is +2, because there is always an attachment sample per project
        String biologicalSampleCodeBlood = createBarcodeFromInteger(sizes[0] + 1 );
        String biologicalSampleCodeTumor = createBarcodeFromInteger(sizes[0] + 2 );

        // offset is +3, because of the previous created sample and the attachement
        String testSampleCodeBlood = createBarcodeFromInteger(sizes[0] + 3);
        String testSampleCodeTumor = createBarcodeFromInteger(sizes[0] + 4);
        String patientId = CODE + "ENTITY-" + (sizes[1] + 1);

        patientSampleIdPair[0] = patientId;
        patientSampleIdPair[1] = testSampleCodeTumor;

        // Logging code block
        log.debug(String.format("Number of non-entity samples: %s", sizes[0]));
        log.info(String.format("%s: New patient ID created %s", AppInfo.getAppInfo(), patientSampleIdPair[0]));
        log.info(String.format("%s: New tumor sample ID created %s", AppInfo.getAppInfo(), patientSampleIdPair[1]));
        log.info(String.format("%s: New tumor DNA sample ID created %s", AppInfo.getAppInfo(), biologicalSampleCodeTumor));
        log.info(String.format("%s: New blood sample ID created %s", AppInfo.getAppInfo(), biologicalSampleCodeBlood));
        log.info(String.format("%s: New blood DNA sample ID created %s", AppInfo.getAppInfo(), testSampleCodeBlood));

        // In case registration fails, return null
        
        if(!registerNewPatient(patientId, biologicalSampleCodeTumor,
                biologicalSampleCodeBlood, testSampleCodeTumor, testSampleCodeBlood))
            patientSampleIdPair = null;
        
    }


    /**
     * Determines the number of non entity samples and
     * total samples for a project
     * @return An 1D array with 2 entries:
     *          array[0] = number of non entities
     *          array[1] = number of total entities
     */
    private int[] getNumberOfSampleTypes(){
        int[] sizes = new int[2];
        List<Sample> sampleList = openBisClient.getSamplesOfProject(PROJECTID);
        List<Sample> entities = getEntities(sampleList);

        String highestID = null;
        // Filter sample list for Q_BIOLOGICAL_SAMPLE and Q_TEST_SAMPLE
        for(Sample s : sampleList){
            if (s.getSampleTypeCode().equals("Q_BIOLOGICAL_SAMPLE") || s.getSampleTypeCode().equals("Q_TEST_SAMPLE")){
                String idCount = s.getCode().substring(5, 9);
                if (highestID == null){
                    highestID = idCount;
                } else {
                    if (isIdHigher(highestID, idCount))
                        highestID = idCount;
                }
            }
        }
        log.info(convertIdToInt(highestID));
        sizes[0] = convertIdToInt(highestID);
        sizes[1] = entities.size();
        return sizes;
    }

    private boolean isIdHigher(String idA, String idB){

        // Compare the letter first
        // eg: B < Z 
        if (idB.toCharArray()[3] > idA.toCharArray()[3])
            return true;
        // B < Z
        if (idB.toCharArray()[3] < idA.toCharArray()[3])
            return false;
        // Compare the digits
        // eg. 002 > 001
        if (Integer.parseInt(idB.substring(0,3)) > Integer.parseInt(idA.substring(0,3)))
            return true;
        return false;
    }

    private int convertIdToInt(String id){
        int iterator = Integer.parseInt(id.substring(0, 3));
        char idChar = id.charAt(3);

        int multiplier = 0;
        for (int i=0; i<ALPHABET.length; i++){
            if (idChar == ALPHABET[i])
                multiplier = i;
        }
        return multiplier * 1000 + iterator;  
    }

    @Override
    public String[] getNewPatientSampleIdPair() {
        return patientSampleIdPair;
    }

    @Override
    public boolean checkIfPatientExists(String sampleID) {
        Sample sample;
        try{
            sample = openBisClient.getSampleByIdentifier("/" + SPACE + "/" + sampleID);
        } catch (Exception exc){
            log.error(exc);
            return false;
        }
        return sample != null;
    }

    @Override
    public String addNewSampleToPatient(String patientID, String filterProperty) {
        List<Sample> sampleList = openBisClient.getSamplesWithParentsAndChildren(patientID).get(0).getChildren();

        if (sampleList.size() == 0){
            log.error(String.format("Sample list was empty, patient ID %s seems to have no parents or childrens", patientID));
            return "";
        }

        List<Sample> biologicalSamplesOnly = sampleList.stream().filter(sample -> sample.getSampleTypeCode()
                .equals("Q_BIOLOGICAL_SAMPLE"))
                .collect(Collectors.toList())
                .stream().filter(sample -> sample.getPropertiesJson().containsValue(filterProperty)).collect(Collectors.toList());

        int size = biologicalSamplesOnly.size();

        if (size == 0){
            log.error(String.format("No samples of type 'Q_BIOLOGICAL_SAMPLE' found for patient ID %s.", patientID));
            return "";
        } else if (size > 1){
            log.error(String.format("More than 1 sample of type 'Q_BIOLOGICAL_SAMPLE' found for patient ID %s.", patientID));
            return "";
        }

        int i = getNumberOfSampleTypes()[0];

        String sampleBarcode = createBarcodeFromInteger(getNumberOfSampleTypes()[0] + 2);

        if (sampleBarcode.isEmpty()){
            log.error("Retrieval of a new sample barcode failed. No new sample for an existing patient " +
                    "was created.");
            return "";
        }

        boolean sampleRegistrationWasSuccessfull = registerTestSample(sampleBarcode, biologicalSamplesOnly.get(0).getCode());

        return sampleRegistrationWasSuccessfull ? sampleBarcode : "";
    }

    @Override
    public Collection<String> getRegisteredPatients() {
        return openBisClient.getSamplesOfProjectBySearchService(PROJECTID).stream().filter(sample -> sample.getSampleTypeCode()
                .equals("Q_BIOLOGICAL_ENTITY"))
                .map(Sample::getCode)
                .collect(Collectors.toList());
    }

    /**
     * Registration of a new patient with samples
     * @param patientId A code for the sample type Q_BIOLOGICAL_ENTITY
     * @param biologicalSampleCodeBlood A code for the sample type Q_BIOLOGICAL_SAMPLE (Blood)
     * @param biologicalSampleCodeTumor A code for the sample type Q_BIOLOGICAL_SAMPLE (Tumor)
     * @param testSampleCodeBlood A code for the sample type Q_TEST_SAMPLE (Blood)
     * @param testSampleCodeTumor A code for the sample type Q_TEST_SAMPLE (Tumor)
     * @return True, if registration was successful, else false
     */
    private boolean registerNewPatient(String patientId, String biologicalSampleCodeTumor, String biologicalSampleCodeBlood,
                                       String testSampleCodeTumor, String testSampleCodeBlood) {
        if (registerEntity(patientId) &&
                registerBioSample(biologicalSampleCodeTumor, "/"+SPACE+"/"+patientId, "tumor tissue") &&
                registerTestSample(testSampleCodeTumor, "/"+SPACE+"/"+biologicalSampleCodeTumor) &&
                registerBioSample(biologicalSampleCodeBlood, "/"+SPACE+"/"+patientId, "blood") &&
                registerTestSample(testSampleCodeBlood, "/"+SPACE+"/"+biologicalSampleCodeBlood)){
            return true;
        }

        return false;
    }

    /**
     * Registration of a new test sample
     * @param testSampleCode A code for the test sample type Q_TEST_SAMPLE
     * @param parent A code for the parent sample type Q_BIOLOGICAL_SAMPLE
     * @return True, if registration was successful, else false
     */
    private boolean registerTestSample(String testSampleCode, String parent) {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        List<String> parents = new ArrayList<>();

        metadata.put("Q_SAMPLE_TYPE", "DNA");
        parents.add(parent);

        map.put("code", testSampleCode);
        map.put("space", SPACE);
        map.put("project", CODE);
        map.put("experiment", CODE + "E3");
        map.put("type", "Q_TEST_SAMPLE");
        map.put("metadata", metadata);
        map.put("parents", parents);

        params.put(testSampleCode, map);

        try{
            openBisClient.ingest("DSS1", "register-sample-batch", params);
        } catch (Exception exc){
            log.error(exc);
            return false;
        }

        return true;
    }

    /**
     * Registration of a new biological sample
     * @param biologicalSampleCode A code for the sample type Q_BIOLOGICAL_SAMPLE
     * @param parent A code for the parent sample type Q_BIOLOGICAL_ENTITY
     * @return True, if registration was successful, else false
     */
    private boolean registerBioSample(String biologicalSampleCode, String parent, String tissue) {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        List<String> parents = new ArrayList<>();

        if (tissue.equals("blood")){
            metadata.put("Q_PRIMARY_TISSUE", "PBMC");
        } else {
            metadata.put("Q_PRIMARY_TISSUE", "TUMOR_TISSUE_UNSPECIFIED");
        }
        metadata.put("Q_TISSUE_DETAILED", tissue);
        parents.add(parent);

        map.put("code", biologicalSampleCode);
        map.put("space", SPACE);
        map.put("project", CODE);
        map.put("experiment", CODE + "E2");
        map.put("type", "Q_BIOLOGICAL_SAMPLE");
        map.put("metadata", metadata);
        map.put("parents", parents);

        params.put(biologicalSampleCode, map);

        try{
            openBisClient.ingest("DSS1", "register-sample-batch", params);
        } catch (Exception exc){
            log.error(exc);
            return false;
        }

        return true;
    }

    /**
     * Registration of a new test sample
     * @param patientId A code for the sample type Q_BIOLOGICAL_ENTITY
     * @return True, if registration was successful, else false
     */
    private boolean registerEntity(String patientId){
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("Q_NCBI_ORGANISM", "9606");

        map.put("code", patientId);
        map.put("space", SPACE);
        map.put("project", CODE);
        map.put("experiment", CODE + "E1");
        map.put("type", "Q_BIOLOGICAL_ENTITY");
        map.put("metadata", metadata);


        params.put(patientId, map);

        try{
            openBisClient.ingest("DSS1", "register-sample-batch", params);
        } catch (Exception exc){
            log.error(exc);
            return false;
        }

        return true;
    }


    /**
     * Get a sample list with samples from type
     * 'Q_BIOLOGICAL_ENTITY' from a list of samples
     * @param sampleList The sample list to be filtered
     * @return The filtered list
     */
    private List<Sample> getEntities(List<Sample> sampleList){
        List<Sample> filteredList = new ArrayList<>();

        for(Sample s : sampleList){
            if (s.getSampleTypeCode().equals("Q_BIOLOGICAL_ENTITY")){
                filteredList.add(s);
            }
        }

        return filteredList;

    }

    /**
     * Creates a complete barcode from a given number using
     * the global project code prefix.
     * Checksum calculation and barcode verification is included.
     * @param number An integer number
     * @return A fully formatted valid QBiC barcode
     */
    private String createBarcodeFromInteger(int number){
        int multiplicator = number / 1000;
        char letter = ALPHABET[multiplicator];

        int remainingCounter = number - multiplicator*1000;

        if (remainingCounter > 999 || remainingCounter < 0){
            return "";
        }

        String preBarcode = CODE + Utils.createCountString(remainingCounter, 3) + letter;

        String barcode = preBarcode + checksum(preBarcode);

        if (!BarcodeFunctions.isQbicBarcode(barcode)){
            log.error(String.format("%s: Barcode created from Integer is not a valid barcode: %s", AppInfo.getAppInfo(),
                    barcode));
            barcode = "";
        }

        return barcode;

    }

}
