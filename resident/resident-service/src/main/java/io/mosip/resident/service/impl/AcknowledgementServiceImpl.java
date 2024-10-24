package io.mosip.resident.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.mosip.resident.util.TemplateValueFromTemplateTypeCodeAndLangCode;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.templatemanager.spi.TemplateManager;
import io.mosip.kernel.core.templatemanager.spi.TemplateManagerBuilder;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.constant.RequestType;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.entity.ResidentTransactionEntity;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.repository.ResidentTransactionRepository;
import io.mosip.resident.service.AcknowledgementService;
import io.mosip.resident.util.TemplateUtil;
import io.mosip.resident.util.Utility;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * This class is used to create service class implementation for getting acknowledgement API.
 * @Author Kamesh Shekhar Prasad
 */
@Service
public class AcknowledgementServiceImpl implements AcknowledgementService {

    private static final Logger logger = LoggerConfiguration.logConfig(AcknowledgementServiceImpl.class);

    @Autowired
    private ResidentTransactionRepository residentTransactionRepository;

    @Autowired
    private TemplateUtil templateUtil;

    @Autowired
    private TemplateValueFromTemplateTypeCodeAndLangCode templateValueFromTemplateTypeCodeAndLangCode;

    private static final String CLASSPATH = "classpath";
    private static final String ENCODE_TYPE = "UTF-8";

    private TemplateManager templateManager;

    @Autowired
    private TemplateManagerBuilder templateManagerBuilder;

    @PostConstruct
    public void idTemplateManagerPostConstruct() {
        templateManager = templateManagerBuilder.encodingType(ENCODE_TYPE).enableCache(false).resourceLoader(CLASSPATH)
                .build();
    }

    @Autowired
    private Utility utility;

    @Override
    public Tuple2<byte[], RequestType> getAcknowledgementPDF(String eventId, String languageCode, int timeZoneOffset, String locale) throws ResidentServiceCheckedException, IOException {
        logger.debug("AcknowledgementServiceImpl::getAcknowledgementPDF()::entry");

            Optional<ResidentTransactionEntity> residentTransactionEntity = residentTransactionRepository
                    .findById(eventId);
            String requestTypeCode;
            if (residentTransactionEntity.isPresent()) {
                requestTypeCode = residentTransactionEntity.get().getRequestTypeCode();
            } else {
                throw new ResidentServiceCheckedException(ResidentErrorCode.EVENT_STATUS_NOT_FOUND);
            }
            RequestType requestType = RequestType.getRequestTypeFromString(requestTypeCode);
            Tuple2<Map<String, String>, String> ackTemplateVariables = requestType.getAckTemplateVariables(templateUtil, residentTransactionEntity.get(), languageCode, timeZoneOffset, locale);
			String requestProperty = ackTemplateVariables.getT2();
            String fileText = templateValueFromTemplateTypeCodeAndLangCode.getTemplateValueFromTemplateTypeCodeAndLangCode(languageCode, requestProperty);
            Map<String, String> templateVariables = ackTemplateVariables.getT1();
            InputStream stream = new ByteArrayInputStream(fileText.getBytes(StandardCharsets.UTF_8));
            InputStream templateValue = templateManager.merge(stream, convertMapValueFromStringToObject(templateVariables));
            logger.debug("AcknowledgementServiceImpl::getAcknowledgementPDF()::exit");
            return Tuples.of(utility.signPdf(templateValue, null), requestType);
    }

    public Map<String, Object> convertMapValueFromStringToObject(Map<String, String> templateVariables) {
        Map<String, Object> templateMapObject = new HashMap<>();
        for(Map.Entry<String, String> entry: templateVariables.entrySet()){
            templateMapObject.put(entry.getKey(), entry.getValue());
        }
        return templateMapObject;
    }

}

