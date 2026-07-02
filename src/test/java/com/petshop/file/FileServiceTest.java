package com.petshop.file;

import com.petshop.common.BusinessException;
import com.petshop.config.QiniuConfig;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class FileServiceTest {

    @Test
    public void testUpload_EmptyFile() {
        QiniuService qiniuService = new QiniuService();
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            qiniuService.upload(emptyFile, "images");
        });
        assertEquals("上传文件不能为空", exception.getMessage());
    }
}
