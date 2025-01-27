package com.tyron.code.ui.file.action.file;

import static com.tyron.code.ui.file.tree.TreeUtil.updateNode;

import androidx.appcompat.app.AlertDialog;

import com.tyron.code.ui.component.tree.TreeNode;
import com.tyron.code.ui.component.tree.helper.TreeHelper;
import com.tyron.code.ui.file.tree.TreeUtil;
import com.tyron.code.ui.file.tree.model.TreeFile;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.R;
import com.tyron.code.ui.file.action.ActionContext;
import com.tyron.code.ui.file.action.FileAction;
import com.tyron.common.util.StringSearch;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import kotlin.io.FileWalkDirection;
import kotlin.io.FilesKt;

public class DeleteFileAction extends FileAction {
    @Override
    public boolean isApplicable(File file) {
        return true;
    }

    @Override
    public void addMenu(ActionContext context) {
        context.getMenu().add("Delete")
                .setOnMenuItemClickListener(menuItem -> {
                    new AlertDialog.Builder(context.getFragment().requireContext())
                            .setMessage(String.format(context.getFragment().getString(R.string.dialog_confirm_delete),
                                    context.getCurrentNode().getValue().getFile().getName()))
                            .setPositiveButton(context.getFragment().getString(R.string.dialog_delete), (d, which) -> {
                                if (deleteFiles(context)) {
                                    context.getTreeView().deleteNode(context.getCurrentNode());
                                } else {
                                    new AlertDialog.Builder(context.getFragment().requireContext())
                                            .setTitle(R.string.error)
                                            .setMessage("Failed to delete file.")
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                }
                            })
                            .show();
                    return true;
                });
    }

    private boolean deleteFiles(ActionContext context) {
        File currentFile = context.getCurrentNode().getContent().getFile();
        FilesKt.walk(currentFile, FileWalkDirection.TOP_DOWN).iterator().forEachRemaining(file -> {
            if (file.getName().endsWith(".java")) { // todo: add .kt and .xml checks
                context.getFragment().getMainViewModel().removeFile(file);

                Module module = ProjectManager.getInstance()
                        .getCurrentProject()
                        .getModule(file);
                if (module instanceof JavaModule) {
                    String packageName = StringSearch.packageName(file);
                    if (packageName != null) {
                        packageName += "." + file.getName()
                                .substring(0, file.getName().lastIndexOf("."));
                    }
                    ((JavaModule) module).removeJavaFile(packageName);
                }
            }
        });
        try {
            FileUtils.forceDelete(currentFile);
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
