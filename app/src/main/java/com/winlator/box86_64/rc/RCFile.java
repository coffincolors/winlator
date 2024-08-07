package com.winlator.box86_64.rc;

import android.content.Context;

import androidx.annotation.NonNull;

import com.winlator.core.FileUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RCFile implements Comparable<RCFile> {
    public final int id;
    private String name = "";
    private ArrayList<RCGroup> groups = new ArrayList<>();
    private final Context context;

    public RCFile(Context context, int id) {
        this.context = context;
        this.id = id;
    }

    public List<RCGroup> getGroups() {
        return groups;
    }

    public void removeGroup(RCGroup group) {
        groups.remove(group);
    }

    public void save() {
        File file = getRCFile(context, id);

        try {
            JSONObject profileData = new JSONObject();
            profileData.put("id", id);
            profileData.put("name", name);
            JSONArray groupsJSONArray = new JSONArray();

            for (RCGroup group : groups) {
                JSONObject groupData = new JSONObject();
                groupData.put("name", group.getGroupName());
                groupData.put("desc", group.getGroupDesc());
                groupData.put("enabled", group.isEnabled());
                JSONArray itemsJSONArray = new JSONArray();

                for (RCItem item : group.getItems()) {
                    JSONObject itemData = new JSONObject();
                    itemData.put("processName", item.getProcessName());
                    itemData.put("desc", item.getItemDesc());
                    itemData.put("vars", new JSONObject(item.getVarMap()));
                    itemsJSONArray.put(itemData);
                }
                groupData.put("items", itemsJSONArray);
                groupsJSONArray.put(groupData);
            }

            profileData.put("groups", groupsJSONArray);

            FileUtils.writeString(file, profileData.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public static File getRCFile(Context context, int id) {
        return new File(RCManager.getRCFilesDir(context), "box86_64rc-" + id + ".rcp");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(RCFile o) {
        return Integer.compare(id, o.id);
    }
}
