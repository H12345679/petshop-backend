package com.petshop.file;

import com.petshop.common.BusinessException;
import com.petshop.config.QiniuConfig;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 七牛云文件上传服务。把前端传来的文件直传到七牛，返回可公网访问的 URL。
 */
@Service
public class QiniuService {

    @Autowired
    private Auth auth;
    @Autowired
    private UploadManager uploadManager;
    @Autowired
    private QiniuConfig qiniuConfig;

    /**
     * 上传文件到七牛。
     *
     * @param file 前端上传的文件（multipart）
     * @param dir  存放的「目录前缀」，如 images / videos（七牛没有真实目录，只是文件名前缀）
     * @return 可公网访问的完整 URL（domain + "/" + key）
     */
    public String upload(MultipartFile file, String dir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        // 1) 生成唯一文件名：dir/UUID.后缀，避免重名互相覆盖
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.')) : "";
        String key = dir + "/" + UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            // 2) 用 AK/SK 针对目标 bucket 生成「上传凭证」
            String token = auth.uploadToken(qiniuConfig.getBucket());
            // 3) 把文件字节流直传到七牛
            Response res = uploadManager.put(file.getBytes(), key, token);
            if (!res.isOK()) {
                throw new BusinessException("七牛上传失败：" + res.bodyString());
            }
            // 4) 返回最终可访问 URL = 域名 + / + key
            return qiniuConfig.getDomain() + "/" + key;
        } catch (QiniuException e) {
            throw new BusinessException("七牛上传异常：" + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败：" + e.getMessage());
        }
    }
}
