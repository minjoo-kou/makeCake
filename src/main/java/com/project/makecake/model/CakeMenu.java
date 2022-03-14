package com.project.makecake.model;

import com.project.makecake.backOffice.dto.CakeMenuRowDto;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@Entity
@NoArgsConstructor
public class CakeMenu extends Timestamped{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long cakeMenuId;

    @Column
    private String type;

    @Column
    private String size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CakeMenuPriceState priceState;

    @Column
    private String price;

    @Column
    private String moreInfo;

    @ManyToOne
    @JoinColumn(name="STORE_ID")
    private Store store;

    //생성자
    public CakeMenu(CakeMenuRowDto menuRowDto, Store store, CakeMenuPriceState priceState){
        this.type = menuRowDto.getType();
        this.size = menuRowDto.getSize();
        this.price = menuRowDto.getPrice();
        this.moreInfo = menuRowDto.getMoreInfo();
        this.priceState = priceState;
        this.store = store;
    }
}
