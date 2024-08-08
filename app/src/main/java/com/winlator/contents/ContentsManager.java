package com.winlator.contents;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;

import org.json.JSONObject;

import java.io.File;

public class ContentsManager {
    public enum InstallFailedReason {
        ERROR_NOSPACE,
        ERROR_BADTAR,
        ERROR_NOPROFILE,
        ERROR_BADPROFILE,
        ERROR_MISSINGFILES,
        ERROR_UNKNOWN
    }

    public enum ContentDirName {
        CONTENT_MAIN_DIR_NAME("contents"),
        CONTENT_WINE_DIR_NAME("wine"),
        CONTENT_TURNIP_DIR_NAME("turnip"),
        CONTENT_VIRGL_DIR_NAME("virgl"),
        CONTENT_DXVK_DIR_NAME("dxvk"),
        CONTENT_VKD3D_DIR_NAME("vkd3d");

        private String name;

        ContentDirName(String name) {
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private Context context;

    public ContentsManager(Context context) {
        this.context = context;
    }

    public interface OnInstallFinishedCallback {
        void onFailed(InstallFailedReason reason, Exception e);

        void onSucceed(ContentProfile profile);
    }

    public void syncContents(boolean clean) {
        // 创建Profile列表
        // 按类型搜索目录
        // 找到目录后判断是否包含profile
        // 若不包含则直接删除目录
        // 若包含在尝试读取
        // 读取失败直接删除目录
        // 读取后验证文件列表
        // 验证失败直接删除目录
        // 全部成功则放入Profile列表
    }

    public void installContentFile(Uri uri, OnInstallFinishedCallback callback) {
        // 清理临时文件夹
        // 创建临时文件夹
        File file = new File(context.getFilesDir(), "tmp/" + ContentDirName.CONTENT_MAIN_DIR_NAME);
        FileUtils.delete(file);
        file.mkdirs();
        // 尝试解压文件
        // 若失败则回调
        boolean ret;
        ret = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, uri, file);
        if (!ret)
            ret = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, uri, file);
        if (!ret) {
            callback.onFailed(InstallFailedReason.ERROR_BADTAR, null);
            return;
        }
        // 成功则尝试读取 Profile
        // 失败则回调
        // 尝试验证 Profile
        // 失败则回调
        // 将解压的文件移动到指定位置
        // 执行成功回调
    }

    public ContentProfile loadProfile(File file) {
        // TODO:
        try {
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
        } catch (Exception e) {

        }
        return null;
    }
}
