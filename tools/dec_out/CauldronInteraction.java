package net.minecraft.core.cauldron;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public interface CauldronInteraction {
   Map<Item, CauldronInteraction> f_175606_ = m_175617_();
   Map<Item, CauldronInteraction> f_175607_ = m_175617_();
   Map<Item, CauldronInteraction> f_175608_ = m_175617_();
   Map<Item, CauldronInteraction> f_175609_ = m_175617_();
   CauldronInteraction f_175610_ = (p_175683_, p_175684_, p_175685_, p_175686_, p_175687_, p_175688_) -> m_175618_(
         p_175684_,
         p_175685_,
         p_175686_,
         p_175687_,
         p_175688_,
         Blocks.f_152476_.m_49966_().m_61124_(LayeredCauldronBlock.f_153514_, Integer.valueOf(3)),
         SoundEvents.f_11778_
      );
   CauldronInteraction f_175611_ = (p_175676_, p_175677_, p_175678_, p_175679_, p_175680_, p_175681_) -> m_175618_(
         p_175677_, p_175678_, p_175679_, p_175680_, p_175681_, Blocks.f_152477_.m_49966_(), SoundEvents.f_11780_
      );
   CauldronInteraction f_175612_ = (p_175669_, p_175670_, p_175671_, p_175672_, p_175673_, p_175674_) -> m_175618_(
         p_175670_,
         p_175671_,
         p_175672_,
         p_175673_,
         p_175674_,
         Blocks.f_152478_.m_49966_().m_61124_(LayeredCauldronBlock.f_153514_, Integer.valueOf(3)),
         SoundEvents.f_144076_
      );
   CauldronInteraction f_175613_ = (p_175662_, p_175663_, p_175664_, p_175665_, p_175666_, p_175667_) -> {
      Block $$6 = Block.m_49814_(p_175667_.m_41720_());
      if (!($$6 instanceof ShulkerBoxBlock)) {
         return InteractionResult.PASS;
      } else {
         if (!p_175663_.f_46443_) {
            ItemStack $$7 = new ItemStack(Blocks.f_50456_);
            if (p_175667_.m_41782_()) {
               $$7.m_41751_(p_175667_.m_41783_().m_6426_());
            }

            p_175665_.m_21008_(p_175666_, $$7);
            p_175665_.m_36220_(Stats.f_12947_);
            LayeredCauldronBlock.m_153559_(p_175662_, p_175663_, p_175664_);
         }

         return InteractionResult.m_19078_(p_175663_.f_46443_);
      }
   };
   CauldronInteraction f_175614_ = (p_278890_, p_278891_, p_278892_, p_278893_, p_278894_, p_278895_) -> {
      if (BannerBlockEntity.m_58504_(p_278895_) <= 0) {
         return InteractionResult.PASS;
      } else {
         if (!p_278891_.f_46443_) {
            ItemStack $$6 = p_278895_.m_255036_(1);
            BannerBlockEntity.m_58509_($$6);
            if (!p_278893_.m_150110_().f_35937_) {
               p_278895_.m_41774_(1);
            }

            if (p_278895_.m_41619_()) {
               p_278893_.m_21008_(p_278894_, $$6);
            } else if (p_278893_.m_150109_().m_36054_($$6)) {
               p_278893_.f_36095_.m_150429_();
            } else {
               p_278893_.m_36176_($$6, false);
            }

            p_278893_.m_36220_(Stats.f_12946_);
            LayeredCauldronBlock.m_153559_(p_278890_, p_278891_, p_278892_);
         }

         return InteractionResult.m_19078_(p_278891_.f_46443_);
      }
   };
   CauldronInteraction f_175615_ = (p_175629_, p_175630_, p_175631_, p_175632_, p_175633_, p_175634_) -> {
      if (!(p_175634_.m_41720_() instanceof DyeableLeatherItem $$7)) {
         return InteractionResult.PASS;
      } else if (!$$7.m_41113_(p_175634_)) {
         return InteractionResult.PASS;
      } else {
         if (!p_175630_.f_46443_) {
            $$7.m_41123_(p_175634_);
            p_175632_.m_36220_(Stats.f_12945_);
            LayeredCauldronBlock.m_153559_(p_175629_, p_175630_, p_175631_);
         }

         return InteractionResult.m_19078_(p_175630_.f_46443_);
      }
   };

   static Object2ObjectOpenHashMap<Item, CauldronInteraction> m_175617_() {
      return Util.m_137469_(
         new Object2ObjectOpenHashMap(),
         p_175646_ -> p_175646_.defaultReturnValue(
               (CauldronInteraction)(p_175739_, p_175740_, p_175741_, p_175742_, p_175743_, p_175744_) -> InteractionResult.PASS
            )
      );
   }

   InteractionResult m_175710_(BlockState p_175711_, Level p_175712_, BlockPos p_175713_, Player p_175714_, InteractionHand p_175715_, ItemStack p_175716_);

   static void m_175649_() {
      m_175647_(f_175606_);
      f_175606_.put(Items.f_42589_, (p_175732_, p_175733_, p_175734_, p_175735_, p_175736_, p_175737_) -> {
         if (PotionUtils.m_43579_(p_175737_) != Potions.f_43599_) {
            return InteractionResult.PASS;
         } else {
            if (!p_175733_.f_46443_) {
               Item $$6 = p_175737_.m_41720_();
               p_175735_.m_21008_(p_175736_, ItemUtils.m_41813_(p_175737_, p_175735_, new ItemStack(Items.f_42590_)));
               p_175735_.m_36220_(Stats.f_12944_);
               p_175735_.m_36246_(Stats.f_12982_.m_12902_($$6));
               p_175733_.m_46597_(p_175734_, Blocks.f_152476_.m_49966_());
               p_175733_.m_5594_(null, p_175734_, SoundEvents.f_11769_, SoundSource.BLOCKS, 1.0F, 1.0F);
               p_175733_.m_142346_(null, GameEvent.f_157769_, p_175734_);
            }

            return InteractionResult.m_19078_(p_175733_.f_46443_);
         }
      });
      m_175647_(f_175607_);
      f_175607_.put(
         Items.f_42446_,
         (p_175725_, p_175726_, p_175727_, p_175728_, p_175729_, p_175730_) -> m_175635_(
               p_175725_,
               p_175726_,
               p_175727_,
               p_175728_,
               p_175729_,
               p_175730_,
               new ItemStack(Items.f_42447_),
               p_175660_ -> p_175660_.m_61143_(LayeredCauldronBlock.f_153514_) == 3,
               SoundEvents.f_11781_
            )
      );
      f_175607_.put(Items.f_42590_, (p_175718_, p_175719_, p_175720_, p_175721_, p_175722_, p_175723_) -> {
         if (!p_175719_.f_46443_) {
            Item $$6 = p_175723_.m_41720_();
            p_175721_.m_21008_(p_175722_, ItemUtils.m_41813_(p_175723_, p_175721_, PotionUtils.m_43549_(new ItemStack(Items.f_42589_), Potions.f_43599_)));
            p_175721_.m_36220_(Stats.f_12944_);
            p_175721_.m_36246_(Stats.f_12982_.m_12902_($$6));
            LayeredCauldronBlock.m_153559_(p_175718_, p_175719_, p_175720_);
            p_175719_.m_5594_(null, p_175720_, SoundEvents.f_11770_, SoundSource.BLOCKS, 1.0F, 1.0F);
            p_175719_.m_142346_(null, GameEvent.f_157816_, p_175720_);
         }

         return InteractionResult.m_19078_(p_175719_.f_46443_);
      });
      f_175607_.put(Items.f_42589_, (p_175704_, p_175705_, p_175706_, p_175707_, p_175708_, p_175709_) -> {
         if (p_175704_.m_61143_(LayeredCauldronBlock.f_153514_) != 3 && PotionUtils.m_43579_(p_175709_) == Potions.f_43599_) {
            if (!p_175705_.f_46443_) {
               p_175707_.m_21008_(p_175708_, ItemUtils.m_41813_(p_175709_, p_175707_, new ItemStack(Items.f_42590_)));
               p_175707_.m_36220_(Stats.f_12944_);
               p_175707_.m_36246_(Stats.f_12982_.m_12902_(p_175709_.m_41720_()));
               p_175705_.m_46597_(p_175706_, p_175704_.m_61122_(LayeredCauldronBlock.f_153514_));
               p_175705_.m_5594_(null, p_175706_, SoundEvents.f_11769_, SoundSource.BLOCKS, 1.0F, 1.0F);
               p_175705_.m_142346_(null, GameEvent.f_157769_, p_175706_);
            }

            return InteractionResult.m_19078_(p_175705_.f_46443_);
         } else {
            return InteractionResult.PASS;
         }
      });
      f_175607_.put(Items.f_42463_, f_175615_);
      f_175607_.put(Items.f_42462_, f_175615_);
      f_175607_.put(Items.f_42408_, f_175615_);
      f_175607_.put(Items.f_42407_, f_175615_);
      f_175607_.put(Items.f_42654_, f_175615_);
      f_175607_.put(Items.f_42660_, f_175614_);
      f_175607_.put(Items.f_42667_, f_175614_);
      f_175607_.put(Items.f_42728_, f_175614_);
      f_175607_.put(Items.f_42671_, f_175614_);
      f_175607_.put(Items.f_42672_, f_175614_);
      f_175607_.put(Items.f_42669_, f_175614_);
      f_175607_.put(Items.f_42673_, f_175614_);
      f_175607_.put(Items.f_42663_, f_175614_);
      f_175607_.put(Items.f_42668_, f_175614_);
      f_175607_.put(Items.f_42665_, f_175614_);
      f_175607_.put(Items.f_42662_, f_175614_);
      f_175607_.put(Items.f_42661_, f_175614_);
      f_175607_.put(Items.f_42666_, f_175614_);
      f_175607_.put(Items.f_42670_, f_175614_);
      f_175607_.put(Items.f_42727_, f_175614_);
      f_175607_.put(Items.f_42664_, f_175614_);
      f_175607_.put(Items.f_42266_, f_175613_);
      f_175607_.put(Items.f_42273_, f_175613_);
      f_175607_.put(Items.f_42229_, f_175613_);
      f_175607_.put(Items.f_42225_, f_175613_);
      f_175607_.put(Items.f_42226_, f_175613_);
      f_175607_.put(Items.f_42275_, f_175613_);
      f_175607_.put(Items.f_42227_, f_175613_);
      f_175607_.put(Items.f_42269_, f_175613_);
      f_175607_.put(Items.f_42274_, f_175613_);
      f_175607_.put(Items.f_42271_, f_175613_);
      f_175607_.put(Items.f_42268_, f_175613_);
      f_175607_.put(Items.f_42267_, f_175613_);
      f_175607_.put(Items.f_42272_, f_175613_);
      f_175607_.put(Items.f_42224_, f_175613_);
      f_175607_.put(Items.f_42228_, f_175613_);
      f_175607_.put(Items.f_42270_, f_175613_);
      f_175608_.put(
         Items.f_42446_,
         (p_175697_, p_175698_, p_175699_, p_175700_, p_175701_, p_175702_) -> m_175635_(
               p_175697_, p_175698_, p_175699_, p_175700_, p_175701_, p_175702_, new ItemStack(Items.f_42448_), p_175651_ -> true, SoundEvents.f_11783_
            )
      );
      m_175647_(f_175608_);
      f_175609_.put(
         Items.f_42446_,
         (p_175690_, p_175691_, p_175692_, p_175693_, p_175694_, p_175695_) -> m_175635_(
               p_175690_,
               p_175691_,
               p_175692_,
               p_175693_,
               p_175694_,
               p_175695_,
               new ItemStack(Items.f_151055_),
               p_175627_ -> p_175627_.m_61143_(LayeredCauldronBlock.f_153514_) == 3,
               SoundEvents.f_144089_
            )
      );
      m_175647_(f_175609_);
   }

   static void m_175647_(Map<Item, CauldronInteraction> p_175648_) {
      p_175648_.put(Items.f_42448_, f_175611_);
      p_175648_.put(Items.f_42447_, f_175610_);
      p_175648_.put(Items.f_151055_, f_175612_);
   }

   static InteractionResult m_175635_(
      BlockState p_175636_,
      Level p_175637_,
      BlockPos p_175638_,
      Player p_175639_,
      InteractionHand p_175640_,
      ItemStack p_175641_,
      ItemStack p_175642_,
      Predicate<BlockState> p_175643_,
      SoundEvent p_175644_
   ) {
      if (!p_175643_.test(p_175636_)) {
         return InteractionResult.PASS;
      } else {
         if (!p_175637_.f_46443_) {
            Item $$9 = p_175641_.m_41720_();
            p_175639_.m_21008_(p_175640_, ItemUtils.m_41813_(p_175641_, p_175639_, p_175642_));
            p_175639_.m_36220_(Stats.f_12944_);
            p_175639_.m_36246_(Stats.f_12982_.m_12902_($$9));
            p_175637_.m_46597_(p_175638_, Blocks.f_50256_.m_49966_());
            p_175637_.m_5594_(null, p_175638_, p_175644_, SoundSource.BLOCKS, 1.0F, 1.0F);
            p_175637_.m_142346_(null, GameEvent.f_157816_, p_175638_);
         }

         return InteractionResult.m_19078_(p_175637_.f_46443_);
      }
   }

   static InteractionResult m_175618_(
      Level p_175619_, BlockPos p_175620_, Player p_175621_, InteractionHand p_175622_, ItemStack p_175623_, BlockState p_175624_, SoundEvent p_175625_
   ) {
      if (!p_175619_.f_46443_) {
         Item $$7 = p_175623_.m_41720_();
         p_175621_.m_21008_(p_175622_, ItemUtils.m_41813_(p_175623_, p_175621_, new ItemStack(Items.f_42446_)));
         p_175621_.m_36220_(Stats.f_12943_);
         p_175621_.m_36246_(Stats.f_12982_.m_12902_($$7));
         p_175619_.m_46597_(p_175620_, p_175624_);
         p_175619_.m_5594_(null, p_175620_, p_175625_, SoundSource.BLOCKS, 1.0F, 1.0F);
         p_175619_.m_142346_(null, GameEvent.f_157769_, p_175620_);
      }

      return InteractionResult.m_19078_(p_175619_.f_46443_);
   }
}
