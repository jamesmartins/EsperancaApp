package br.com.cpb.esperanca.fragment;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import br.com.cpb.esperanca.activity.LibraryActivity;
import br.com.cpb.esperanca.app.Reader;
import br.com.cpb.esperanca.iab.IabHelper;
import br.com.cpb.esperanca.iab.IabResult;
import br.com.cpb.esperanca.iab.Inventory;
import br.com.cpb.esperanca.iab.Purchase;
import br.com.cpb.esperanca.model.Book;
import br.com.cpb.esperanca.service.DownloadBookService;
import br.com.cpb.esperanca.util.Utils;

import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;

import br.com.cpb.esperanca.R;
import roboguice.inject.InjectView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: castelanjr
 * Date: 3/18/13
 * Time: 4:20 PM
 * Copyright (C) 2013 Nyvra Software. All rights reserved.
 */
public class AccountFragment extends RoboSherlockFragment implements View.OnClickListener, Reader.OnAuthenticateUserListener {

    private boolean mBeingShowed;
    private Animation mAnimationIn, mAnimationOut;
    private Reader mReader;

    @InjectView(R.id.button_restore)
    private Button mButtonRestore;

    @InjectView(R.id.layout_login)
    private LinearLayout mLayoutLogin;

    @InjectView(R.id.edit_username)
    private EditText mEditUsername;

    @InjectView(R.id.edit_password)
    private EditText mEditPassword;

    @InjectView(R.id.button_login)
    private Button mButtonLogin;

    @InjectView(R.id.layout_logged)
    private LinearLayout mLayoutLogged;

    @InjectView(R.id.text_logged)
    private TextView mTextLogged;

    @InjectView(R.id.button_logout)
    private Button mButtonLogout;

    @InjectView(R.id.progress_account)
    private ProgressBar mProgress;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAnimationOut = AnimationUtils.loadAnimation(getActivity(), R.anim.out_from_top);
        mAnimationIn = AnimationUtils.loadAnimation(getActivity(), R.anim.in_to_top);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mButtonLogin.setOnClickListener(this);
        mButtonLogout.setOnClickListener(this);
        mButtonRestore.setOnClickListener(this);

        mEditPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onClick(mButtonLogin);
                    handled = true;
                }
                return handled;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();

        mLayoutLogin.setVisibility(View.GONE);
        mLayoutLogged.setVisibility(View.GONE);
        mProgress.setVisibility(View.GONE);
        mReader = Reader.getInstance(getActivity());

//        new AsyncTask<Void, Void, Boolean>() {
//
//            @Override
//            protected void onPreExecute() {
//                mProgress.setVisibility(View.VISIBLE);
//                mLayoutLogin.setVisibility(View.GONE);
//                mLayoutLogged.setVisibility(View.GONE);
//            }
//
//            @Override
//            protected Boolean doInBackground(Void... params) {
//                mReader = Reader.getInstance(getActivity());
//                mReader.setAuthenticateUserListener(AccountFragment.this);
//                if (mReader.getCurrentUser() == null) {
//                    return false;
//                } else {
//                    return true;
//                }
//            }
//
//            @Override
//            protected void onPostExecute(Boolean logged) {
//                mProgress.setVisibility(View.GONE);
//                if (logged) {
//                    mLayoutLogin.setVisibility(View.GONE);
//                    mLayoutLogged.setVisibility(View.VISIBLE);
//
//                    mTextLogged.setText("Ol��, " + mReader.getCurrentUser().getFullName());
//                } else {
//                    mLayoutLogin.setVisibility(View.VISIBLE);
//                    mLayoutLogged.setVisibility(View.GONE);
//                }
//            }
//        }.execute();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_login) {
            if (mEditUsername.getText() != null && mEditUsername.getText().toString().trim().length() > 0 &&
                mEditPassword.getText() != null && mEditPassword.getText().toString().trim().length() > 0) {

                Utils.dismissKeyboard(getActivity());

                mProgress.setVisibility(View.VISIBLE);
                mLayoutLogin.setVisibility(View.GONE);
                mButtonRestore.setVisibility(View.GONE);
                mReader.authenticateUser(mEditUsername.getText().toString(), mEditPassword.getText().toString());
            } else {
                Utils.showToast(getActivity(), "Por favor, preencha os campos corretamente.");
            }
        } else if (id == R.id.button_logout) {
            mReader.logout();
            mLayoutLogged.setVisibility(View.GONE);
            mLayoutLogin.setVisibility(View.VISIBLE);
        } else if (id == R.id.button_restore) {

            if (!mReader.isInAppBillingEnabled()) {
                Utils.showIABDisabledDialog(getActivity());
                return;
            }

            final ProgressDialog dialog = ProgressDialog.show(getActivity(), "Restaurando livros comprados", "Aguarde", true, false);
            dialog.setCancelable(false);
            mReader.queryInventory(new IabHelper.QueryInventoryFinishedListener() {
                @Override
                public void onQueryInventoryFinished(IabResult result, final Inventory inv) {
                    if (result.isFailure() || inv == null || inv.getAllPurchases() == null || inv.getAllPurchases().size() == 0) {
                        dialog.setMessage("Nenhum produto para restaurar encontrado.");
                        getView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                            }
                        }, 1000);
                        return;
                    }
                    final HashMap<Integer, Purchase> purchasesHashMap = new HashMap<Integer, Purchase>();
                    final List<Integer> ids = new ArrayList<Integer>();
                    for (Purchase purchase : inv.getAllPurchases()) {
                        String sku = purchase.getSku();
                        String[] dismantledSku = sku.split("\\.");
                        if (dismantledSku.length == 0) {
                            continue;
                        }
                        int id = Integer.valueOf(dismantledSku[dismantledSku.length - 1]);
                        boolean owned = false;
                        for (Book book : mReader.getOwnedBooks()) {
                            if (book.id == id) {
                                owned = true;
                                break;
                            }
                        }
                        if (owned) {
                            continue;
                        }

                        ids.add(id);
                        purchasesHashMap.put(id, purchase);

                        mReader.setBookDetailsListener(new Reader.OnLoadBookDetailsListener() {
                            @Override
                            public void onSuccess(Book book) {
                                if (dialog != null) {
                                    dialog.setMessage("Restaurando livro " + book.title);
                                }
                                Reader.getInstance(getActivity()).restoreBook(book, purchasesHashMap.get(book.id).getOrderId());

                                if (ids.indexOf(book.id) == ids.size() - 1) {
                                    if (dialog != null) dialog.dismiss();

                                    Intent intent = new Intent(getActivity(), LibraryActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                    startActivity(intent);
                                    getActivity().overridePendingTransition(R.anim.grow_from_middle, R.anim.shrink_to_middle);
                                }
                            }

                            @Override
                            public void onFailure() {
                                if (dialog != null) {
                                    dialog.setMessage("Ocorreu um erro ao restaurar o livro");
                                    getView().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialog.dismiss();
                                        }
                                    }, 1000);
                                }
                            }

                            @Override
                            public void onParseFailure() {
                                if (dialog != null) {
                                    dialog.setMessage("Ocorreu um erro ao restaurar o livro");
                                    getView().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            dialog.dismiss();
                                        }
                                    }, 1000);
                                }
                            }
                        });
                        mReader.loadBookById(id);
                        if (dialog != null) {
                            dialog.setMessage("Buscando informações do livro previamente comprado");
                        }

                    }
                    if (ids.size() == 0) {
                        if (dialog != null) {
                            dialog.setMessage("Nenhum produto para restaurar encontrado.");
                            getView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                dialog.dismiss();
                            }
                        }, 1000);
                        }
                    }
                }
            });
        }
    }

    public boolean isBeingShowed() {
        return mBeingShowed;
    }

    public void toggleVisibility() {
        if (mBeingShowed) {
            getView().setVisibility(View.GONE);
            getView().startAnimation(mAnimationOut);
            mBeingShowed = false;
        } else {
            getView().setVisibility(View.VISIBLE);
            getView().startAnimation(mAnimationIn);
            mBeingShowed = true;
        }
    }

    public void hide() {
        getView().setVisibility(View.GONE);
        mBeingShowed = false;
    }

    @Override
    public void onSuccess() {
        mEditUsername.setText("");
        mEditPassword.setText("");
        mProgress.setVisibility(View.GONE);
        mLayoutLogged.setVisibility(View.VISIBLE);
        mButtonRestore.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFailure() {
        mProgress.setVisibility(View.GONE);
        mLayoutLogin.setVisibility(View.VISIBLE);
        mButtonRestore.setVisibility(View.VISIBLE);
        Utils.showToast(getActivity(), "Ocorreu uma falha na autenticação do usuário. Por favor, tente novamente.");
    }

    @Override
    public void onParseFailure() {
        mProgress.setVisibility(View.GONE);
        mLayoutLogin.setVisibility(View.VISIBLE);
        mButtonRestore.setVisibility(View.VISIBLE);
        Utils.showToast(getActivity(), "Ocorreu uma falha na autenticação do usuário. Por favor, tente novamente.");
    }
}
