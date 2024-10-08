package com.flutter.DataPreprocessingService.service.document_processing;

import com.flutter.DataPreprocessingService.entity.DocumentMetadata;
import com.flutter.DataPreprocessingService.repository.document_meta.DocumentMetadataRepository;
import com.flutter.DataPreprocessingService.service.indexing.ElasticsearchIndexingService;
import com.flutter.DataPreprocessingService.service.pdf_parse.PdfParsingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessingService.class);
    private final DocumentMetadataRepository documentMetadataRepository;
    private final PdfParsingService pdfParsingService;
    private final ElasticsearchIndexingService elasticsearchIndexingService;

    /**
     * 문서 청킹 및 인덱싱을 수행한다.
     */
    public void processChunkingAndIndexing() {
        logger.info("청킹이 완료되지 않은 문서 목록 조회 시작");
        List<DocumentMetadata> pendingDocuments = documentMetadataRepository.findByChunkingStatusNot(DocumentMetadata.ChunkingStatus.COMPLETED);

        for (DocumentMetadata document : pendingDocuments) {
            try {
                logger.info("문서 청킹 시작: {}", document.getFilePath());
                List<File> chunkedFiles = pdfParsingService.chunkPdf(document.getFilePath(), 50);

                for (File chunk : chunkedFiles) {
                    Map<String, Object> parsedResult = pdfParsingService.analyzeDocumentWithUpstage(chunk.getAbsolutePath());

                    if (parsedResult != null && !parsedResult.isEmpty()) {
                        logger.info("문서 파싱 완료, Elasticsearch에 저장 시작");
                        elasticsearchIndexingService.saveToElasticsearch(parsedResult, document);

                        if (chunk.delete()) {
                            logger.info("청크 파일 삭제 성공: {}", chunk.getAbsolutePath());
                        } else {
                            logger.error("청크 파일 삭제 실패: {}", chunk.getAbsolutePath());
                        }
                    } else {
                        logger.error("API 호출 실패로 인해 청크 파일을 삭제하지 않음: {}", chunk.getAbsolutePath());
                    }
                }

                document.setChunkingStatus(DocumentMetadata.ChunkingStatus.COMPLETED);
                documentMetadataRepository.save(document);
                logger.info("문서 청킹 및 인덱싱 완료: {}", document.getFilePath());

            } catch (IOException e) {
                logger.error("문서 청킹 및 인덱싱 실패: {}", document.getFilePath(), e);
            }
        }
    }
}
