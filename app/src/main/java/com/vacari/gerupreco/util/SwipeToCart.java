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
 * Arrastar a linha para a direita adiciona o produto ao carrinho.
 *
 * <p>Diferente do uso classico do {@link ItemTouchHelper}, o gesto nao remove
 * nada: o produto continua no catalogo. Por isso quem recebe o callback precisa
 * chamar {@code notifyItemChanged(position)} para o card voltar ao lugar - sem
 * isso ele fica parado fora da tela, porque o ItemTouchHelper espera que a
 * linha suma.</p>
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

    private final Callback<Integer> onSwipe;

    private final Drawable icon;
    private final Paint background;
    private final RectF bounds = new RectF();
    private final float radius;
    private final int iconMargin;

    public SwipeToCart(Context context, Callback<Integer> onSwipe) {
        super(0, ItemTouchHelper.RIGHT);
        this.onSwipe = onSwipe;

        icon = ContextCompat.getDrawable(context, R.drawable.ic_add_shopping_cart_24);
        icon.setTint(ContextCompat.getColor(context, R.color.on_primary));

        background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(ContextCompat.getColor(context, R.color.primary_container));

        radius = context.getResources().getDimension(R.dimen.radius_lg);
        iconMargin = context.getResources().getDimensionPixelSize(R.dimen.space_margin_x);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
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
        onSwipe.callback(position);
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;

        if (dX > 0) {
            canvas.save();
            // Recorta na parte revelada, mas desenha o retangulo arredondado
            // sobre a linha inteira: assim o canto que aparece e o mesmo do
            // card, e nao um canto redondo acompanhando o dedo.
            canvas.clipRect(itemView.getLeft(), itemView.getTop(),
                    itemView.getLeft() + dX, itemView.getBottom());

            bounds.set(itemView.getLeft(), itemView.getTop(),
                    itemView.getRight(), itemView.getBottom());
            canvas.drawRoundRect(bounds, radius, radius, background);

            drawIcon(canvas, itemView);
            canvas.restore();
        }

        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    private void drawIcon(Canvas canvas, View itemView) {
        int top = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
        int left = itemView.getLeft() + iconMargin;

        icon.setBounds(left, top,
                left + icon.getIntrinsicWidth(), top + icon.getIntrinsicHeight());
        icon.draw(canvas);
    }
}
