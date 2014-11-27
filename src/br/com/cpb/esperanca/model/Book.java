package br.com.cpb.esperanca.model;

import android.content.Context;

public class Book {
    public int id, category_id;
    public String title, author, price, cover_url, issue_url, description, isbn, pages_number;
    
    @Override
    public String toString() {
        return title;
    }

    public String getPath(Context context) {
        return context.getExternalFilesDir(null) + "/books/" + id + ".epub";
    }
    
    public String getAssetPath(String bookName) {
        return "file:///android_asset/" + "/books/" + bookName + ".epub";
    }

    public String getSku() {
        return "br.com.cpb.cpbstore." + id;
    }

    public String getCode() {
        String[] dismantledUrl = issue_url.split("download/");
        return dismantledUrl[dismantledUrl.length - 1];
    }

    public String getFreeURL() {
        String code = getCode();
        return "https://ws.cpb.com.br/apps/cpbreader/Produtos/downloadAndroid/" + code;
    }

    public String getPaidUrl(String orderId) {
        return getFreeURL() + "/" + orderId;
    }

}
