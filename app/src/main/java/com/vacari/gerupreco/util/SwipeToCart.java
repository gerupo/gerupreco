package com.vacari.gerupreco.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.vacari.gerupreco.R;

/**
 * Arrastar a linha da lista de produtos mexe no carrinho: para a direita
 * adiciona, para a esquerda tira.
 *
 * <p>Diferente do uso classico do {@link ItemTouchHelper}, o gesto nao remove
 * nada da lista: o produto continua no catalogo. Por isso quem recebe o
 * callback precisa chamar {@code notifyItemChanged(position)} para o card
 * voltar ao lugar - sem isso ele fica parado fora da tela, porque o
 * ItemTouchHelper espera que a linha suma.</p>
 *
 * <p>O arrasto para a esquerda so e liberado nos produtos que estao no
 * carrinho ({@link Host#isInCart}). Num produto que nao esta la o gesto nao
 * teria o que remover, e a resistencia do proprio card diz isso sem texto
 * nenhum.</p>
 *
 * <p>O fundo e desenhado no proprio canvas em vez de vir de uma view atras do
 * card: o item da lista e um MaterialCardView solto no RecyclerView, e por um
 * fundo real embaixo dele seria preciso envolver cada linha num container so
 * para isso.</p>
 */
public class SwipeToCart extends ItemTouchHelper.SimpleCallback {

    /**
     * Fracao da largura que confirma a acao. Abaixo do padrao (0.5) porque a
     * lista e usada de pe no mercado, com uma mao so.
     */
    private static final float SWIPE_THRESHOLD = 0.35f;

    /**
     * Quem sabe o estado do carrinho e o que fazer com cada gesto.
     */
    public interface Host {

        boolean isInCart(int position);

        void addToCart(int position);

        void removeFromCart(int position);
    }

    private final Host host;

    private final Drawable addIcon;
    private final Drawable removeIcon;
    private final Paint addBackground;
    private final Paint removeBackground;
    private final RectF bounds = new RectF();
    private final float radius;
    private final int iconMargin;

    public SwipeToCart(Context context, Host host) {
        super(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT);
        this.host = host;

        addIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_shopping_cart_24);
        addIcon.setTint(ContextCompat.getColor(context, R.color.on_primary));

        removeIcon = ContextCompat.getDrawable(context, R.drawable.ic_remove_shopping_cart_24);
        removeIcon.setTint(ContextCompat.getColor(context, R.color.on_error_container));

        addBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        addBackground.setColor(ContextCompat.getColor(context, R.color.primary_container));

        removeBackground = new Paint(Paint.ANTI_ALIAS_FLAG);
        removeBackground.setColor(ContextCompat.getColor(context, R.color.error_container));

        radius = context.getResources().getDimension(R.dimen.radius_lg);
        iconMargin = context.getResources().getDimensionPixelSize(R.dimen.space_margin_x);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    /**
     * Adicionar vale para qualquer produto; remover, so para os que estao no
     * carrinho.
     */
    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder) {
        int position = viewHolder.getBindingAdapterPosition();

        if (position != RecyclerView.NO_POSITION && host.isInCart(position)) {
            return ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT;
        }

        return ItemTouchHelper.RIGHT;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return SWIPE_THRESHOLD;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        int position = viewHolder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        // O card volta para o lugar sem deixar rastro visual do que aconteceu;
        // a vibracao confirma o toque junto com o Toast.
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CONFIRM);

        if (direction == ItemTouchHelper.LEFT) {
            host.removeFromCart(position);
            return;
        }

        host.addToCart(position);
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;

        if (dX > 0) {
            drawAction(canvas, itemView, itemView.getLeft(), itemView.getLeft() + dX,
                    addBackground, addIcon, true);
        } else if (dX < 0) {
            drawAction(canvas, itemView, itemView.getRight() + dX, itemView.getRight(),
                    removeBackground, removeIcon, false);
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    /**
     * Recorta na parte revelada, mas desenha o retangulo arredondado sobre a
     * linha inteira: assim o canto que aparece e o mesmo do card, e nao um
     * canto redondo acompanhando o dedo.
     */
    private void drawAction(Canvas canvas, View itemView, float clipLeft, float clipRight,
                            Paint background, Drawable icon, boolean iconAtStart) {
        canvas.save();
        canvas.clipRect(clipLeft, itemView.getTop(), clipRight, itemView.getBottom());

        bounds.set(itemView.getLeft(), itemView.getTop(),
                itemView.getRight(), itemView.getBottom());
        canvas.drawRoundRect(bounds, radius, radius, background);

        drawIcon(canvas, itemView, icon, iconAtStart);
        canvas.restore();
    }

    private void drawIcon(Canvas canvas, View itemView, Drawable icon, boolean iconAtStart) {
        int top = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
        int left = iconAtStart
                ? itemView.getLeft() + iconMargin
                : itemView.getRight() - iconMargin - icon.getIntrinsicWidth();

        icon.setBounds(left, top,
                left + icon.getIntrinsicWidth(), top + icon.getIntrinsicHeight());
        icon.draw(canvas);
    }
}
