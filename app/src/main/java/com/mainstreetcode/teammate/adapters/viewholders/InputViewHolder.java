package com.mainstreetcode.teammate.adapters.viewholders;

import android.arch.core.util.Function;
import android.content.res.ColorStateList;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import com.mainstreetcode.teammate.R;
import com.mainstreetcode.teammate.fragments.headless.ImageWorkerFragment;
import com.mainstreetcode.teammate.model.Item;
import com.mainstreetcode.teammate.util.ModelUtils;
import com.mainstreetcode.teammate.util.Supplier;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * Viewholder for editing simple text fields for an {@link Item}
 */
public class InputViewHolder<T extends ImageWorkerFragment.ImagePickerListener> extends BaseItemViewHolder<T>
        implements
        TextWatcher {

    EditText editText;
    private final ImageButton button;
    private final TextInputLayout inputLayout;
    private final Supplier<Boolean> enabler;
    private final Function<CharSequence, CharSequence> errorChecker;

    @Nullable private Function<Item, Boolean> visibilitySupplier;

    public InputViewHolder(View itemView, Supplier<Boolean> enabler, @Nullable Function<CharSequence, CharSequence> errorChecker) {
        super(itemView);
        this.enabler = enabler;
        this.errorChecker = errorChecker == null ? this::emptyTextChecker : errorChecker;
        inputLayout = itemView.findViewById(R.id.input_layout);
        button = itemView.findViewById(R.id.button);
        editText = inputLayout.getEditText();

        inputLayout.setEnabled(isEnabled());
        editText.addTextChangedListener(this);
    }

    public InputViewHolder(View itemView, Supplier<Boolean> enabler) {
        this(itemView, enabler, null);
    }

    public InputViewHolder setButtonRunnable(@DrawableRes int icon, Runnable clickRunnable) {
        return setButtonRunnable(input -> true, icon, clickRunnable);
    }

    public InputViewHolder setButtonRunnable(Function<Item, Boolean> visibilitySupplier, @DrawableRes int icon, Runnable clickRunnable) {
        this.visibilitySupplier = visibilitySupplier;
        button.setImageResource(icon);
        button.setOnClickListener(clicked -> clickRunnable.run());
        return this;
    }

    @Override
    public void bind(Item item) {
        super.bind(item);
        inputLayout.setEnabled(isEnabled());
        inputLayout.setHint(itemView.getContext().getString(item.getStringRes()));
        editText.setText(ModelUtils.processString(item.getValue()));
        editText.setInputType(item.getInputType());

        checkForErrors();
        setClickableState();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Nothing
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        // Nothing
    }

    @Override
    public void afterTextChanged(Editable editable) {
        item.setValue(editable.toString());
        checkForErrors();
    }

    private void checkForErrors() {
        CharSequence errorMessage = errorChecker.apply(editText.getText());
        if (TextUtils.isEmpty(errorMessage)) editText.setError(null);
        else editText.setError(errorMessage);
    }

    private void setClickableState() {
        int colorInt = ContextCompat.getColor(itemView.getContext(), isEnabled() ? R.color.black : R.color.disabled_text);
        editText.setTextColor(ColorStateList.valueOf(colorInt));

        int visibility = item == null || visibilitySupplier == null ? GONE : visibilitySupplier.apply(item) ? VISIBLE : GONE;
        button.setVisibility(visibility);
    }

    private CharSequence emptyTextChecker(CharSequence input) {
        return TextUtils.isEmpty(input) ? editText.getContext().getString(R.string.team_invalid_empty_field) : "";
    }

    protected boolean isEnabled() {
        try {return enabler.get();}
        catch (Exception e) {return false;}
    }
}
